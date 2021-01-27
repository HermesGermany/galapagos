package com.hermesworld.ais.galapagos.applications.impl;

import com.hermesworld.ais.galapagos.applications.*;
import com.hermesworld.ais.galapagos.certificates.CaManager;
import com.hermesworld.ais.galapagos.certificates.CertificateSignResult;
import com.hermesworld.ais.galapagos.events.GalapagosEventManager;
import com.hermesworld.ais.galapagos.events.GalapagosEventSink;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.InitPerCluster;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.naming.ApplicationPrefixes;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.util.TimeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApplicationsServiceImpl implements ApplicationsService, InitPerCluster {

    private final KafkaClusters kafkaClusters;

    private final TopicBasedRepository<KnownApplicationImpl> knownApplicationsSource;

    private final TopicBasedRepository<ApplicationOwnerRequest> requestsRepository;

    private final CurrentUserService currentUserService;

    private final TimeService timeService;

    private final NamingService namingService;

    private final GalapagosEventManager eventManager;

    private static final String TOPIC_NAME = "application-metadata";

    private static final String KNOWN_APPLICATIONS_TOPIC_NAME = "known-applications";

    private static final String REQUESTS_TOPIC_NAME = "application-owner-requests";

    private static final Comparator<ApplicationOwnerRequest> requestComparator = (r1, r2) -> r2.getLastStatusChangeAt()
            .compareTo(r1.getLastStatusChangeAt());

    @Autowired
    public ApplicationsServiceImpl(KafkaClusters kafkaClusters, CurrentUserService currentUserService,
            TimeService timeService, NamingService namingService, GalapagosEventManager eventManager) {
        this.kafkaClusters = kafkaClusters;
        this.knownApplicationsSource = kafkaClusters.getGlobalRepository(KNOWN_APPLICATIONS_TOPIC_NAME,
                KnownApplicationImpl.class);
        this.requestsRepository = kafkaClusters.getGlobalRepository(REQUESTS_TOPIC_NAME, ApplicationOwnerRequest.class);
        this.currentUserService = currentUserService;
        this.timeService = timeService;
        this.namingService = namingService;
        this.eventManager = eventManager;
    }

    @Override
    public void init(KafkaCluster cluster) {
        getRepository(cluster).getObjects();
    }

    @Override
    public List<? extends KnownApplication> getKnownApplications(boolean excludeUserApps) {
        List<? extends KnownApplication> apps = internalGetKnownApplications();
        if (!excludeUserApps) {
            return apps;
        }

        List<String> userApps = getUserApplicationsApprovedOrSubmitted().stream().map(KnownApplication::getId)
                .collect(Collectors.toList());
        return apps.stream().filter(app -> !userApps.contains(app.getId())).collect(Collectors.toList());
    }

    @Override
    public Optional<KnownApplication> getKnownApplication(String applicationId) {
        return internalGetKnownApplications().stream().filter(app -> applicationId.equals(app.getId())).findFirst();
    }

    @Override
    public Optional<ApplicationMetadata> getApplicationMetadata(String environmentId, String applicationId) {
        return kafkaClusters.getEnvironment(environmentId)
                .flatMap(cluster -> getRepository(cluster).getObject(applicationId));
    }

    @Override
    public CompletableFuture<ApplicationOwnerRequest> submitApplicationOwnerRequest(String applicationId,
            String comments) {
        String userName = currentUserService.getCurrentUserName().orElse(null);
        if (userName == null) {
            return noUser();
        }

        if (getKnownApplication(applicationId).isEmpty()) {
            return unknownApplication(applicationId);
        }

        Optional<ApplicationOwnerRequest> existing = getRequestsRepository().getObjects().stream()
                .filter(req -> userName.equals(req.getUserName()) && applicationId.equals(req.getApplicationId()))
                .findAny();
        if (existing.isPresent() && (existing.get().getState() == RequestState.SUBMITTED
                || existing.get().getState() == RequestState.APPROVED)) {
            return CompletableFuture.completedFuture(existing.get());
        }

        ApplicationOwnerRequest request;
        if (existing.isPresent()) {
            request = existing.get();
        }
        else {
            request = new ApplicationOwnerRequest();
            request.setId(UUID.randomUUID().toString());
            request.setCreatedAt(timeService.getTimestamp());
        }
        request.setApplicationId(applicationId);
        request.setState(RequestState.SUBMITTED);
        request.setUserName(userName);
        request.setNotificationEmailAddress(currentUserService.getCurrentUserEmailAddress().orElse(null));
        request.setComments(comments);
        request.setLastStatusChangeAt(timeService.getTimestamp());
        request.setLastStatusChangeBy(userName);

        GalapagosEventSink eventSink = eventManager
                .newEventSink(kafkaClusters.getEnvironment(kafkaClusters.getProductionEnvironmentId()).orElse(null));

        return getRequestsRepository().save(request)
                .thenCompose(o -> eventSink.handleApplicationOwnerRequestCreated(request)).thenApply(o -> request);
    }

    @Override
    public List<ApplicationOwnerRequest> getUserApplicationOwnerRequests() {
        String userName = currentUserService.getCurrentUserName().orElse(null);
        if (userName == null) {
            return Collections.emptyList();
        }

        return getRequestsRepository().getObjects().stream().filter(req -> userName.equals(req.getUserName()))
                .sorted(requestComparator).collect(Collectors.toList());
    }

    @Override
    public List<ApplicationOwnerRequest> getAllApplicationOwnerRequests() {
        return getRequestsRepository().getObjects().stream().sorted(requestComparator).collect(Collectors.toList());
    }

    @Override
    public CompletableFuture<ApplicationOwnerRequest> updateApplicationOwnerRequest(String requestId,
            RequestState newState) {
        String userName = currentUserService.getCurrentUserName().orElse(null);
        if (userName == null) {
            return noUser();
        }

        Optional<ApplicationOwnerRequest> opRequest = getRequestsRepository().getObjects().stream()
                .filter(req -> requestId.equals(req.getId())).findFirst();

        if (opRequest.isEmpty()) {
            return unknownRequest(requestId);
        }

        ApplicationOwnerRequest request = opRequest.get();
        request.setState(newState);
        request.setLastStatusChangeAt(timeService.getTimestamp());
        request.setLastStatusChangeBy(userName);

        GalapagosEventSink eventSink = eventManager
                .newEventSink(kafkaClusters.getEnvironment(kafkaClusters.getProductionEnvironmentId()).orElse(null));

        return getRequestsRepository().save(request)
                .thenCompose(o -> eventSink.handleApplicationOwnerRequestUpdated(request)).thenApply(o -> request);
    }

    @Override
    public CompletableFuture<Boolean> cancelUserApplicationOwnerRequest(String requestId) throws IllegalStateException {
        String userName = currentUserService.getCurrentUserName().orElse(null);
        if (userName == null) {
            return noUser();
        }

        Optional<ApplicationOwnerRequest> opRequest = getRequestsRepository().getObjects().stream()
                .filter(req -> requestId.equals(req.getId())).findFirst();

        if (opRequest.isEmpty()) {
            return unknownRequest(requestId);
        }

        GalapagosEventSink eventSink = eventManager
                .newEventSink(kafkaClusters.getEnvironment(kafkaClusters.getProductionEnvironmentId()).orElse(null));

        ApplicationOwnerRequest request = opRequest.get();
        if (request.getState() == RequestState.SUBMITTED) {
            return getRequestsRepository().delete(request)
                    .thenCompose(o -> eventSink.handleApplicationOwnerRequestCanceled(request))
                    .thenApply(o -> Boolean.TRUE);
        }

        if (request.getState() == RequestState.APPROVED) {
            request.setState(RequestState.RESIGNED);
            request.setLastStatusChangeAt(timeService.getTimestamp());
            request.setLastStatusChangeBy(userName);

            return getRequestsRepository().save(request)
                    .thenCompose(o -> eventSink.handleApplicationOwnerRequestUpdated(request))
                    .thenApply(o -> Boolean.TRUE);

        }
        return CompletableFuture
                .failedFuture(new IllegalStateException("May only cancel requests in state SUBMITTED or APPROVED"));
    }

    @Override
    public List<? extends KnownApplication> getUserApplications() {
        return getUserApplications(Set.of(RequestState.APPROVED));
    }

    @Override
    public boolean isUserAuthorizedFor(String applicationId) {
        return getUserApplicationOwnerRequests().stream().anyMatch(
                req -> req.getState() == RequestState.APPROVED && applicationId.equals(req.getApplicationId()));
    }

    @Override
    public CompletableFuture<ApplicationMetadata> createApplicationCertificateFromCsr(String environmentId,
            String applicationId, String csrData, boolean extendCertificate, OutputStream outputStreamForCerFile) {

        Function<CertificateSignResult, CompletableFuture<CertificateSignResult>> resultHandler = result -> {
            try {
                outputStreamForCerFile.write(result.getCertificatePemData().getBytes(StandardCharsets.UTF_8));
            }
            catch (IOException e) {
                return CompletableFuture.failedFuture(e);
            }
            return CompletableFuture.completedFuture(result);
        };

        if (extendCertificate) {
            ApplicationMetadata existing = getApplicationMetadata(environmentId, applicationId).orElse(null);
            if (existing == null) {
                return unknownApplication(applicationId);
            }
            return registerApplication((caManager, appl) -> caManager
                    .extendApplicationCertificate(existing.getDn(), csrData).thenCompose(resultHandler), environmentId,
                    applicationId);
        }
        else {
            return registerApplication((caManager, appl) -> caManager
                    .createApplicationCertificateFromCsr(applicationId, csrData, appl.getName())
                    .thenCompose(resultHandler), environmentId, applicationId);
        }
    }

    @Override
    public CompletableFuture<ApplicationMetadata> createApplicationCertificateAndPrivateKey(String environmentId,
            String applicationId, OutputStream outputStreamForP12File) {
        return registerApplication((caManager, appl) -> caManager
                .createApplicationCertificateAndPrivateKey(applicationId, appl.getName()).thenCompose(result -> {
                    try {
                        outputStreamForP12File.write(result.getP12Data().orElse(new byte[0]));
                    }
                    catch (IOException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                    return CompletableFuture.completedFuture(result);
                }), environmentId, applicationId);
    }

    @Override
    public CompletableFuture<ApplicationMetadata> resetApplicationPrefixes(String environmentId, String applicationId) {
        KafkaCluster cluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (cluster == null) {
            return unknownEnvironment(environmentId);
        }
        KnownApplication app = getKnownApplication(applicationId).orElse(null);
        if (app == null) {
            return unknownApplication(applicationId);
        }

        return getApplicationMetadata(environmentId, applicationId).map(existing -> {
            ApplicationMetadata newMetadata = new ApplicationMetadata(existing);
            ApplicationPrefixes newPrefixes = namingService.getAllowedPrefixes(app);
            newMetadata.setInternalTopicPrefixes(newPrefixes.getInternalTopicPrefixes());
            newMetadata.setConsumerGroupPrefixes(newPrefixes.getConsumerGroupPrefixes());
            newMetadata.setTransactionIdPrefixes(newPrefixes.getTransactionIdPrefixes());
            return getRepository(cluster).save(newMetadata).thenApply(o -> newMetadata);
        }).orElseGet(() -> CompletableFuture.failedFuture(new NoSuchElementException()));
    }

    /**
     * "Migrates" ApplicationMetadata entries in the <code>galapagos.internal.application-metadata</code> topic. Entries
     * having the property <code>topicPrefix</code> set will be re-written to that topic, where the value of that
     * property will either be added to its <code>internalTopicPrefixes</code> array, or the array will be created,
     * containing only that value. <br>
     * Additionally, all internal topic prefixes (possibly that single one from the old field) are copied to
     * <code>transactionIdPrefixes</code>, if that property is not set or empty for an application. <br>
     * This method is called from the admin job "Update Application ACLs", which must be called after migration to
     * Galapagos 1.8.0.
     */
    @SuppressWarnings({ "deprecation", "removal" })
//    public void migrateApplicationMetadata(KafkaCluster cluster) {
//        log.debug("Migrating application-metadata topic on " + cluster.getId() + "...");
//
//        TopicBasedRepository<ApplicationMetadata> repo = getRepository(cluster);
//        List<CompletableFuture<Void>> saveFutures = repo.getObjects().stream()
//                .filter(app -> app.getTopicPrefix() != null || app.getInternalTopicPrefixes() == null
//                        || app.getInternalTopicPrefixes().isEmpty())
//                .map(app -> repo.save(migrator.apply(app))).collect(Collectors.toList());
//        try {
//            CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0])).get();
//            if (!saveFutures.isEmpty()) {
//                log.info("Migrated " + saveFutures.size() + " application-metadata record(s) on cluster "
//                        + cluster.getId());
//            }
//        }
//        catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//        catch (ExecutionException e) {
//            log.error("Migration of application metadata failed", e);
//        }
//    }

    private List<KnownApplication> internalGetKnownApplications() {
        return knownApplicationsSource.getObjects().stream().sorted().collect(Collectors.toList());
    }

    private CompletableFuture<ApplicationMetadata> registerApplication(
            BiFunction<CaManager, KnownApplication, CompletableFuture<CertificateSignResult>> signResultFutureFn,
            String environmentId, String applicationId) {
        KnownApplication application = getKnownApplication(applicationId).orElse(null);
        if (application == null) {
            return unknownApplication(applicationId);
        }
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return unknownEnvironment(environmentId);
        }

        ApplicationMetadata existing = getApplicationMetadata(environmentId, applicationId).orElse(null);

        // currently, we additionally use ALL previously assigned prefixes here, so extending a certificate does not
        // break running applications. In the future, supporting admin jobs could check for "orphaned" prefixes which
        // then can be removed after responsible team's approval.
        ApplicationPrefixes prefixes = namingService.getAllowedPrefixes(application)
                .combineWith(existing == null ? ApplicationPrefixes.EMPTY : existing);

        GalapagosEventSink eventSink = eventManager.newEventSink(kafkaCluster);

        return signResultFutureFn.apply(kafkaClusters.getCaManager(environmentId).orElseThrow(), application)
                .thenCompose(result -> updateApplicationMetadataFromCertificate(kafkaCluster, existing, applicationId,
                        prefixes, result))
                .thenCompose(a -> {
                    if (existing != null && !existing.getDn().equals(a.getDn())) {
                        return eventSink.handleApplicationCertificateChanged(a, existing.getDn()).thenApply(o -> a);
                    }
                    else if (existing == null) {
                        return eventSink.handleApplicationRegistered(a).thenApply(o -> a);
                    }

                    // no event for certificate extension yet
                    return CompletableFuture.completedFuture(a);
                });
    }

    private CompletableFuture<ApplicationMetadata> updateApplicationMetadataFromCertificate(KafkaCluster kafkaCluster,
            ApplicationMetadata metadataOrNull, String applicationId, ApplicationPrefixes prefixes,
            CertificateSignResult result) {
        ApplicationMetadata newMetadata = metadataOrNull != null ? new ApplicationMetadata(metadataOrNull)
                : new ApplicationMetadata();

        newMetadata.setApplicationId(applicationId);
        newMetadata.setDn(result.getDn());
        newMetadata.setCertificateExpiresAt(ZonedDateTime
                .ofInstant(Instant.ofEpochMilli(result.getCertificate().getNotAfter().getTime()), ZoneId.of("Z")));
        newMetadata.setInternalTopicPrefixes(prefixes.getInternalTopicPrefixes());
        newMetadata.setConsumerGroupPrefixes(prefixes.getConsumerGroupPrefixes());
        newMetadata.setTransactionIdPrefixes(prefixes.getTransactionIdPrefixes());

        return getRepository(kafkaCluster).save(newMetadata).thenApply(o -> newMetadata);
    }

    @Scheduled(initialDelay = 30000, fixedDelayString = "PT6H")
    void removeOldRequests() {
        log.debug("removeOldRequests() by scheduler");

        ZonedDateTime maxAge = timeService.getTimestamp().minusDays(30);

        List<ApplicationOwnerRequest> oldRequests = getRequestsRepository().getObjects().stream()
                .filter(req -> (req.getState() == RequestState.REJECTED || req.getState() == RequestState.REVOKED)
                        && req.getLastStatusChangeAt().isBefore(maxAge))
                .collect(Collectors.toList());

        for (ApplicationOwnerRequest request : oldRequests) {
            log.info("Removing request for user " + request.getUserName() + " and application "
                    + request.getApplicationId() + " because it rejects application access and is older than 30 days");
            getRequestsRepository().delete(request);
        }
    }

    private TopicBasedRepository<ApplicationMetadata> getRepository(KafkaCluster kafkaCluster) {
        return kafkaCluster.getRepository(TOPIC_NAME, ApplicationMetadata.class);
    }

    private TopicBasedRepository<ApplicationOwnerRequest> getRequestsRepository() {
        return requestsRepository;
    }

    private List<? extends KnownApplication> getUserApplicationsApprovedOrSubmitted() {
        return getUserApplications(Set.of(RequestState.APPROVED, RequestState.SUBMITTED));
    }

    private List<? extends KnownApplication> getUserApplications(Set<RequestState> requestStates) {
        String userName = currentUserService.getCurrentUserName()
                .orElseThrow(() -> new IllegalStateException("No currently logged in user"));

        Map<String, KnownApplication> apps = internalGetKnownApplications().stream()
                .collect(Collectors.toMap(KnownApplication::getId, Function.identity()));

        return getRequestsRepository().getObjects().stream()
                .filter(req -> requestStates.contains(req.getState()) && userName.equals(req.getUserName()))
                .map(req -> apps.get(req.getApplicationId())).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static <T> CompletableFuture<T> noUser() {
        return CompletableFuture.failedFuture(new IllegalStateException("No user currently logged in"));
    }

    private static <T> CompletableFuture<T> unknownApplication(String applicationId) {
        return CompletableFuture.failedFuture(new NoSuchElementException("Unknown application ID: " + applicationId));
    }

    private static <T> CompletableFuture<T> unknownRequest(String requestId) {
        return CompletableFuture.failedFuture(new NoSuchElementException("Unknown request ID: " + requestId));
    }

    private static <T> CompletableFuture<T> unknownEnvironment(String environmentId) {
        return CompletableFuture
                .failedFuture(new NoSuchElementException("Unknown Kafka environment: " + environmentId));
    }

}
