package com.hermesworld.ais.galapagos.security.roles.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.events.GalapagosEventManager;
import com.hermesworld.ais.galapagos.events.GalapagosEventSink;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.InitPerCluster;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.security.roles.Role;
import com.hermesworld.ais.galapagos.security.roles.UserRoleData;
import com.hermesworld.ais.galapagos.security.roles.UserRoleService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.TimeService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.hermesworld.ais.galapagos.util.FutureUtil.noUser;

@Service
public class UserRoleServiceImpl implements UserRoleService, InitPerCluster {

    private final KafkaClusters kafkaClusters;

    private final CurrentUserService currentUserService;

    private final ApplicationsService applicationsService;

    private final GalapagosEventManager eventManager;

    private final TimeService timeService;

    private static final String TOPIC_NAME = "user-roles";

    public UserRoleServiceImpl(KafkaClusters kafkaClusters, CurrentUserService currentUserService,
            ApplicationsService applicationsService, GalapagosEventManager eventManager, TimeService timeService) {
        this.kafkaClusters = kafkaClusters;
        this.currentUserService = currentUserService;
        this.applicationsService = applicationsService;
        this.eventManager = eventManager;
        this.timeService = timeService;
    }

    public void init(KafkaCluster cluster) {
        getRepository(cluster).getObjects();
    }

    @Override
    public List<UserRoleData> getAllRoles(String environmentId) {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(getRepository(kafkaCluster).getObjects());
    }

    @Override
    public List<UserRoleData> getRolesForUser(String environmentId, String userName) {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return Collections.emptyList();
        }
        return getRepository(kafkaCluster).getObjects().stream().filter(role -> role.getUserName().equals(userName))
                .collect(Collectors.toList());
    }

    @Override
    public CompletableFuture<Void> deleteUserRoles(String environmentId, String userName) {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }
        List<UserRoleData> data = getAllRoles(environmentId).stream()
                .filter(userRole -> userRole.getUserName().equals(userName)).toList();
        return CompletableFuture
                .allOf(data.stream().map(userRoleData -> getRepository(kafkaCluster).delete(userRoleData))
                        .toArray(CompletableFuture[]::new));
    }

    @Override
    public Map<String, List<UserRoleData>> getAllRolesForCurrentUser() {
        String userName = currentUserService.getCurrentUserName()
                .orElseThrow(() -> new IllegalStateException("No currently logged in user"));
        return kafkaClusters.getEnvironmentsMetadata().stream()
                .map(env -> Map.entry(env.getId(), getRolesForUser(env.getId(), userName)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public CompletableFuture<Void> deleteUserRoleById(String environmentId, String id) {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        List<UserRoleData> data = getAllRoles(environmentId);
        Optional<UserRoleData> userRoleOpt = data.stream().filter(u -> u.getId().equals(id)).findFirst();
        if (userRoleOpt.isPresent()) {
            UserRoleData userRole = userRoleOpt.get();
            return getRepository(kafkaCluster).delete(userRole);
        }
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<UserRoleData> updateRole(String requestId, String environmentId, RequestState newState) {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }
        String userName = currentUserService.getCurrentUserName().orElse(null);
        if (userName == null) {
            return noUser();
        }

        Optional<UserRoleData> opRequest = getRepository(kafkaCluster).getObjects().stream()
                .filter(req -> requestId.equals(req.getId())).findFirst();

        if (opRequest.isEmpty()) {
            return unknownRequest(requestId);
        }

        UserRoleData request = opRequest.get();
        request.setState(newState);
        request.setLastStatusChangeAt(timeService.getTimestamp());
        request.setLastStatusChangeBy(userName);

        GalapagosEventSink eventSink = eventManager
                .newEventSink(kafkaClusters.getEnvironment(kafkaClusters.getProductionEnvironmentId()).orElse(null));

        return getRepository(kafkaCluster).save(request).thenCompose(o -> eventSink.handleRoleRequestUpdated(request))
                .thenApply(o -> request);
    }

    @Override
    public List<UserRoleData> listAllRoles() {
        return kafkaClusters.getEnvironmentIds().stream().map(this::getAllRoles).flatMap(List::stream)
                .collect(Collectors.toList());
    }

    @Override
    public CompletableFuture<UserRoleData> submitRoleRequest(String applicationId, Role role, String environmentId,
            String comments) {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }
        String userName = currentUserService.getCurrentUserName().orElse(null);
        if (userName == null) {
            return noUser();
        }

        if (applicationsService.getKnownApplication(applicationId).isEmpty()) {
            return unknownApplication(applicationId);
        }

        Optional<UserRoleData> existing = getRepository(kafkaCluster)
                .getObjects().stream().filter(req -> userName.equals(req.getUserName())
                        && applicationId.equals(req.getApplicationId()) && environmentId.equals(req.getEnvironmentId()))
                .findAny();
        if (existing.isPresent() && (existing.get().getState() == RequestState.SUBMITTED
                || existing.get().getState() == RequestState.APPROVED)) {
            return CompletableFuture
                    .failedFuture(new IllegalStateException("A role for this application has been already submitted"));
        }

        UserRoleData request;
        if (existing.isPresent()) {
            request = existing.get();
        }
        else {
            request = new UserRoleData();
            request.setId(UUID.randomUUID().toString());
            request.setCreatedAt(timeService.getTimestamp());
        }
        request.setApplicationId(applicationId);
        request.setState(RequestState.SUBMITTED);
        request.setUserName(userName);
        request.setRole(role);
        request.setEnvironmentId(environmentId);
        request.setNotificationEmailAddress(currentUserService.getCurrentUserEmailAddress().orElse(null));
        request.setComments(comments);
        request.setLastStatusChangeAt(timeService.getTimestamp());
        request.setLastStatusChangeBy(userName);

        GalapagosEventSink eventSink = eventManager
                .newEventSink(kafkaClusters.getEnvironment(kafkaClusters.getProductionEnvironmentId()).orElse(null));

        return getRepository(kafkaCluster).save(request).thenCompose(o -> eventSink.handleRoleRequestCreated(request))
                .thenApply(o -> request);
    }

    @Override
    public CompletableFuture<Boolean> cancelUserRoleRequest(String requestId, String environmentId)
            throws IllegalStateException {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }
        String userName = currentUserService.getCurrentUserName().orElse(null);
        if (userName == null) {
            return noUser();
        }

        Optional<UserRoleData> opRequest = getRepository(kafkaCluster).getObjects().stream()
                .filter(req -> requestId.equals(req.getId())).findFirst();

        if (opRequest.isEmpty()) {
            return unknownRequest(requestId);
        }

        GalapagosEventSink eventSink = eventManager
                .newEventSink(kafkaClusters.getEnvironment(kafkaClusters.getProductionEnvironmentId()).orElse(null));

        UserRoleData request = opRequest.get();
        if (request.getState() == RequestState.SUBMITTED) {
            return getRepository(kafkaCluster).delete(request)
                    .thenCompose(o -> eventSink.handleRoleRequestCanceled(request)).thenApply(o -> Boolean.TRUE);
        }

        if (request.getState() == RequestState.APPROVED) {
            request.setState(RequestState.RESIGNED);
            request.setLastStatusChangeAt(timeService.getTimestamp());
            request.setLastStatusChangeBy(userName);

            return getRepository(kafkaCluster).save(request)
                    .thenCompose(o -> eventSink.handleRoleRequestUpdated(request)).thenApply(o -> Boolean.TRUE);

        }
        return CompletableFuture
                .failedFuture(new IllegalStateException("May only cancel requests in state SUBMITTED or APPROVED"));
    }

    private TopicBasedRepository<UserRoleData> getRepository(KafkaCluster kafkaCluster) {
        return kafkaCluster.getRepository(TOPIC_NAME, UserRoleData.class);
    }

    private static <T> CompletableFuture<T> unknownApplication(String applicationId) {
        return CompletableFuture.failedFuture(new NoSuchElementException("Unknown application ID: " + applicationId));
    }

    private static <T> CompletableFuture<T> unknownRequest(String requestId) {
        return CompletableFuture.failedFuture(new NoSuchElementException("Unknown request ID: " + requestId));
    }
}
