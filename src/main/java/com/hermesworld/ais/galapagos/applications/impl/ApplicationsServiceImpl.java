package com.hermesworld.ais.galapagos.applications.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hermesworld.ais.galapagos.applications.*;
import com.hermesworld.ais.galapagos.applications.config.ApplicationsConfig;
import com.hermesworld.ais.galapagos.certificates.CaManager;
import com.hermesworld.ais.galapagos.certificates.CertificateSignResult;
import com.hermesworld.ais.galapagos.events.GalapagosEventManager;
import com.hermesworld.ais.galapagos.events.GalapagosEventSink;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.InitPerCluster;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.util.CertificateUtil;
import com.hermesworld.ais.galapagos.util.TimeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class ApplicationsServiceImpl implements ApplicationsService, InitPerCluster {

	private final KafkaClusters kafkaClusters;

	private final TopicBasedRepository<KnownApplicationImpl> knownApplicationsSource;

	private final TopicBasedRepository<ApplicationOwnerRequest> requestsRepository;

	private final CurrentUserService currentUserService;

	private final TimeService timeService;

	private final String consumerGroupPrefix;

	private final String topicPrefixFormat;

	private final GalapagosEventManager eventManager;

	private static final String TOPIC_NAME = "application-metadata";

	private static final String KNOWN_APPLICATIONS_TOPIC_NAME = "known-applications";

	private static final String REQUESTS_TOPIC_NAME = "application-owner-requests";

	private static final Comparator<ApplicationOwnerRequest> requestComparator = (r1, r2) -> r2.getLastStatusChangeAt()
		.compareTo(r1.getLastStatusChangeAt());

	@Autowired
	public ApplicationsServiceImpl(KafkaClusters kafkaClusters, CurrentUserService currentUserService,
		TimeService timeService, GalapagosEventManager eventManager,
		ApplicationsConfig applicationsConfig) {
		this.kafkaClusters = kafkaClusters;
		this.knownApplicationsSource = kafkaClusters.getGlobalRepository(KNOWN_APPLICATIONS_TOPIC_NAME,
			KnownApplicationImpl.class);
		this.requestsRepository = kafkaClusters.getGlobalRepository(REQUESTS_TOPIC_NAME, ApplicationOwnerRequest.class);
		this.currentUserService = currentUserService;
		this.timeService = timeService;
		this.eventManager = eventManager;
		this.consumerGroupPrefix = applicationsConfig.getConsumerGroupPrefix();
		this.topicPrefixFormat = applicationsConfig.getTopicPrefixFormat();
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
		return kafkaClusters.getEnvironment(environmentId).flatMap(cluster -> getRepository(cluster).getObject(applicationId));
	}

//	@Override
//	public Optional<? extends RegisteredApplication> setKafkaGroupPrefix(RegisteredApplication application, String prefix) {
//		Optional<RegisteredApplicationDao> op = registeredApplicationRepository.findById(application.getId());
//		if (!op.isPresent()) {
//			return Optional.empty();
//		}
//
//		if (!getPotentialKafkaGroupPrefixes(application).contains(prefix)) {
//			throw new IllegalArgumentException("Not a valid prefix for application " + application.getName());
//		}
//
//		RegisteredApplicationDao dao = op.get();
//		dao.setKafkaGroupPrefix(prefix);
//		return Optional.of(toRegAppImpl(registeredApplicationRepository.save(dao)));
//	}

	@Override
	public CompletableFuture<ApplicationOwnerRequest> submitApplicationOwnerRequest(String applicationId,
		String comments) {
		String userName = currentUserService.getCurrentUserName().orElse(null);
		if (userName == null) {
			return noUser();
		}

		if (findKnownApplication(applicationId).isEmpty()) {
			return unknownApplication(applicationId);
		}

		Optional<ApplicationOwnerRequest> existing = getRequestsRepository().getObjects().stream()
			.filter(req -> userName.equals(req.getUserName()) && applicationId.equals(req.getApplicationId())).findAny();
		if (existing.isPresent()
			&& (existing.get().getState() == RequestState.SUBMITTED || existing.get().getState() == RequestState.APPROVED)) {
			return CompletableFuture.completedFuture(existing.get());
		}

		ApplicationOwnerRequest request;
		if (existing.isPresent()) {
			request = existing.get();
		} else {
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
			.thenCompose(o -> eventSink.handleApplicationOwnerRequestCreated(request))
			.thenApply(o -> request);
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
				.thenCompose(o -> eventSink.handleApplicationOwnerRequestCanceled(request)).thenApply(o -> Boolean.TRUE);
		}

		if (request.getState() == RequestState.APPROVED) {
			request.setState(RequestState.RESIGNED);
			request.setLastStatusChangeAt(timeService.getTimestamp());
			request.setLastStatusChangeBy(userName);

			return getRequestsRepository().save(request)
				.thenCompose(o -> eventSink.handleApplicationOwnerRequestUpdated(request)).thenApply(o -> Boolean.TRUE);

		}
		return CompletableFuture.failedFuture(new IllegalStateException("May only cancel requests in state SUBMITTED or APPROVED"));
	}

	@Override
	public List<? extends KnownApplication> getUserApplications() {
		return getUserApplications(Set.of(RequestState.APPROVED));
	}

	@Override
	public boolean isUserAuthorizedFor(String applicationId) {
		return getUserApplicationOwnerRequests().stream()
			.anyMatch(req -> req.getState() == RequestState.APPROVED && applicationId.equals(req.getApplicationId()));
	}

	@Override
	public CompletableFuture<ApplicationMetadata> createApplicationCertificateFromCsr(String environmentId,
		String applicationId, String csrData, String topicPrefix, boolean extendCertificate,
		OutputStream outputStreamForCerFile) {

		Function<CertificateSignResult, CompletableFuture<CertificateSignResult>> resultHandler = result -> {
			try {
				outputStreamForCerFile.write(result.getCertificatePemData().getBytes(StandardCharsets.UTF_8));
			} catch (IOException e) {
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
					.extendApplicationCertificate(existing.getDn(), csrData).thenCompose(resultHandler),
				environmentId, applicationId, existing.getTopicPrefix());
		} else {
			return registerApplication((caManager, appl) -> caManager
					.createApplicationCertificateFromCsr(applicationId, csrData, appl.getName()).thenCompose(resultHandler),
				environmentId, applicationId, topicPrefix);
		}
	}

	@Override
	public CompletableFuture<ApplicationMetadata> createApplicationCertificateAndPrivateKey(String environmentId,
		String applicationId, String topicPrefix, OutputStream outputStreamForP12File) {
		return registerApplication((caManager, appl) -> caManager
			.createApplicationCertificateAndPrivateKey(applicationId, appl.getName()).thenCompose(result -> {
				try {
					outputStreamForP12File.write(result.getP12Data().orElse(new byte[0]));
				} catch (IOException e) {
					return CompletableFuture.failedFuture(e);
				}
				return CompletableFuture.completedFuture(result);
			}), environmentId, applicationId, topicPrefix);
	}

	private List<KnownApplication> internalGetKnownApplications() {
		return knownApplicationsSource.getObjects().stream().sorted().collect(Collectors.toList());
	}

	private CompletableFuture<ApplicationMetadata> registerApplication(
		BiFunction<CaManager, KnownApplication, CompletableFuture<CertificateSignResult>> signResultFutureFn,
		String environmentId, String applicationId, String topicPrefix) {
		KnownApplication application = getKnownApplication(applicationId).orElse(null);
		if (application == null) {
			return unknownApplication(applicationId);
		}
		KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
		if (kafkaCluster == null) {
			return CompletableFuture.failedFuture(new NoSuchElementException("Unknown Kafka environment: " + environmentId));
		}

		// FIXME cleanup all this topic name prefix logic here, in TopicService, and in the TopicNameValidator.
		if (!calculatePotentialKafkaTopicPrefixes(application).contains(topicPrefix)) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Topic Prefix for this application"));
		}

		ApplicationMetadata metadata = getApplicationMetadata(environmentId, applicationId).orElse(null);

		Set<String> groupPrefixes = calculatePotentialKafkaGroupPrefixes(application);

		GalapagosEventSink eventSink = eventManager.newEventSink(kafkaCluster);

		return signResultFutureFn.apply(kafkaClusters.getCaManager(environmentId).orElseThrow(), application)
			.thenCompose(result -> updateApplicationMetadataFromCertificate(kafkaCluster, metadata, applicationId, topicPrefix,
				groupPrefixes, result))
			.thenCompose(a -> eventSink.handleApplicationRegistered(a).thenApply(o -> a));
	}

	private CompletableFuture<ApplicationMetadata> updateApplicationMetadataFromCertificate(KafkaCluster kafkaCluster,
		ApplicationMetadata metadataOrNull,
		String applicationId, String topicPrefix, Set<String> consumerGroupPrefixes, CertificateSignResult result) {
		if (metadataOrNull != null) {
			return updateApplicationCertificateInfo(kafkaCluster, applicationId, topicPrefix, consumerGroupPrefixes, result.getDn(),
				Instant.ofEpochMilli(result.getCertificate().getNotAfter().getTime()));
		} else {
			ApplicationMetadata newMetadata = new ApplicationMetadata();
			newMetadata.setApplicationId(applicationId);
			newMetadata.setCertificateExpiresAt(
				ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getCertificate().getNotAfter().getTime()), ZoneId.of("Z")));
			newMetadata.setDn(result.getDn());

			newMetadata.setTopicPrefix(topicPrefix);
			if (consumerGroupPrefixes != null) {
				newMetadata.setConsumerGroupPrefixes(new ArrayList<>(consumerGroupPrefixes));
			}

			return getRepository(kafkaCluster).save(newMetadata).thenApply(o -> newMetadata);
		}
	}

	private CompletableFuture<ApplicationMetadata> updateApplicationCertificateInfo(KafkaCluster kafkaCluster,
		String applicationId,
		String topicPrefix, Set<String> groupPrefixes, String newDn, Instant newExpiresAt) {
		ApplicationMetadata metadata = getRepository(kafkaCluster).getObject(applicationId).orElse(null);
		if (metadata == null) {
			return CompletableFuture.failedFuture(
				new NoSuchElementException("Application " + applicationId + " has not been registered in this environment yet."));
		}

		ApplicationMetadata newMetadata = new ApplicationMetadata(metadata);
		newMetadata.setDn(newDn);
		newMetadata.setTopicPrefix(topicPrefix);
		newMetadata.setConsumerGroupPrefixes(new ArrayList<>(groupPrefixes));
		newMetadata.setCertificateExpiresAt(newExpiresAt.atZone(ZoneId.systemDefault()));

		GalapagosEventSink eventSink = eventManager.newEventSink(kafkaCluster);

		// in this case, FIRST update the ACLs (via event listeners), THEN (only if successful) save new metadata
		return eventSink.handleApplicationCertificateChanged(newMetadata, metadata.getDn())
			.thenCompose(o -> getRepository(kafkaCluster).save(newMetadata)).thenApply(o -> newMetadata);
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
			log.info("Removing request for user " + request.getUserName() + " and application " + request.getApplicationId()
				+ " because it rejects application access and is older than 30 days");
			getRequestsRepository().delete(request);
		}
	}

	private Optional<KnownApplication> findKnownApplication(String applicationId) {
		return internalGetKnownApplications().stream().filter(app -> applicationId.equals(app.getId())).findFirst();
	}

	private Set<String> calculatePotentialKafkaGroupPrefixes(KnownApplication application) {
		List<String> names = new ArrayList<>();

		names.add(application.getName());
		names.addAll(application.getAliases());
		return names.stream().map(name -> consumerGroupPrefix + CertificateUtil.toAppCn(name).replace("_", "-") + ".")
				.collect(Collectors.toSet());
	}

	private Set<String> calculatePotentialKafkaTopicPrefixes(KnownApplication application) {
		List<String> names = new ArrayList<>();

		names.add(application.getName());
		names.addAll(application.getAliases());
		return names.stream().map(name -> MessageFormat.format(topicPrefixFormat, CertificateUtil.toAppCn(name).replace("_", "-")))
				.collect(Collectors.toSet());
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

}
