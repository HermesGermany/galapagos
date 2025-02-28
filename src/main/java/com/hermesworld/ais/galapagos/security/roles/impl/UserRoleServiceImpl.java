package com.hermesworld.ais.galapagos.security.roles.impl;

import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.InitPerCluster;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
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

    private final TimeService timeService;

    private static final String TOPIC_NAME = "user-roles";

    public UserRoleServiceImpl(KafkaClusters kafkaClusters, CurrentUserService currentUserService,
            TimeService timeService) {
        this.kafkaClusters = kafkaClusters;
        this.currentUserService = currentUserService;
        this.timeService = timeService;
    }

    public void init(KafkaCluster cluster) {
        getRepository(cluster).getObjects();
    }

    @Override
    public CompletableFuture<Void> addUserRole(String environmentId, UserRoleData userRoleData) {
        String userName = currentUserService.getCurrentUserName().orElse(null);
        if (userRoleData.getUserName() == null) {
            return noUser();
        }
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (userRoleByAppIdExistsForUser(environmentId, userRoleData.getUserName(), userRoleData.getApplicationId())) {
            return CompletableFuture.completedFuture(null);
        }
        UserRoleData data = new UserRoleData();
        data.setId(UUID.randomUUID().toString());
        data.setUserName(userRoleData.getUserName());
        data.setState(RequestState.SUBMITTED);
        data.setRole(userRoleData.getRole());
        data.setEnvironment(userRoleData.getEnvironment());
        data.setApplicationId(userRoleData.getApplicationId());
        data.setComments(userRoleData.getComments());
        data.setNotificationEmailAddress(currentUserService.getCurrentUserEmailAddress().orElse(null));
        data.setCreatedAt(timeService.getTimestamp());
        data.setLastStatusChangeAt(timeService.getTimestamp());
        data.setLastStatusChangeBy(userName);
        return getRepository(kafkaCluster).save(data);
    }

    private boolean userRoleByAppIdExistsForUser(String environmentId, String userName, String appId) {
        List<UserRoleData> data = getAllRoles(environmentId);
        return data.stream().anyMatch(u -> u.getUserName().equals(userName) && u.getApplicationId().equals(appId));
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
            return FutureUtil.noop();
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
            return FutureUtil.noop();
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
    public CompletableFuture<Void> updateRole(String requestId, String environmentId, RequestState newState) {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return FutureUtil.noop();
        }
        String userName = currentUserService.getCurrentUserName().orElse(null);
        if (userName == null) {
            return noUser();
        }

        Optional<UserRoleData> opRequest = getRepository(kafkaCluster).getObjects().stream()
                .filter(req -> requestId.equals(req.getId())).findFirst();

        if (opRequest.isEmpty()) {
            return FutureUtil.noop();
        }

        UserRoleData request = opRequest.get();
        request.setState(newState);
        request.setLastStatusChangeAt(timeService.getTimestamp());
        request.setLastStatusChangeBy(userName);

        return getRepository(kafkaCluster).save(request);
    }

    @Override
    public List<UserRoleData> listAllRoles() {
        return kafkaClusters.getEnvironmentIds().stream().map(this::getAllRoles).flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private TopicBasedRepository<UserRoleData> getRepository(KafkaCluster kafkaCluster) {
        return kafkaCluster.getRepository(TOPIC_NAME, UserRoleData.class);
    }

}
