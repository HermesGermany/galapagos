package com.hermesworld.ais.galapagos.security.roles.impl;

import com.hermesworld.ais.galapagos.events.GalapagosEventManager;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.InitPerCluster;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.security.roles.UserRoleData;
import com.hermesworld.ais.galapagos.security.roles.UserRoleService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class UserRoleServiceImpl implements UserRoleService, InitPerCluster {

    private final KafkaClusters kafkaClusters;

    private final CurrentUserService currentUserService;

    private final GalapagosEventManager eventManager;

    private static final String TOPIC_NAME = "user-roles-V2";

    public UserRoleServiceImpl(KafkaClusters kafkaClusters, CurrentUserService currentUserService,
            GalapagosEventManager eventManager) {
        this.kafkaClusters = kafkaClusters;
        this.currentUserService = currentUserService;
        this.eventManager = eventManager;
    }

    public void init(KafkaCluster cluster) {
        getRepository(cluster).getObjects();
    }

    @Override
    public CompletableFuture<Void> addUserRole(String environmentId, UserRoleData userRoleData) {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return CompletableFuture.completedFuture(null);
        }
        UserRoleData data = new UserRoleData();
        data.setId(UUID.randomUUID().toString());
        data.setUserName(userRoleData.getUserName());
        data.setRole(userRoleData.getRole());
        data.setApplicationId(userRoleData.getApplicationId());
        try {
            getRepository(kafkaCluster).save(data).get();
        }
        catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return FutureUtil.noop();
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
    public CompletableFuture<Void> deleteUserRole(String environmentId) {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return FutureUtil.noop();
        }
        String userName = currentUserService.getCurrentUserName()
                .orElseThrow(() -> new IllegalStateException("No currently logged in user"));
        List<UserRoleData> data = getAllRoles(environmentId);
        data.stream().filter(userRole -> userRole.getUserName().equals(userName));
        // .forEach(userRole -> getRepository(kafkaCluster).delete(userRole));
        for (UserRoleData userRole : data) {
            getRepository(kafkaCluster).delete(userRole);
        }
        return FutureUtil.noop();
    }

    @Override
    public List<UserRoleData> getAllRolesForCurrentUser() {
        String userName = currentUserService.getCurrentUserName()
                .orElseThrow(() -> new IllegalStateException("No currently logged in user"));
        return kafkaClusters.getEnvironmentsMetadata().stream()
                .flatMap(env -> getRolesForUser(env.getId(), userName).stream()).collect(Collectors.toList());
    }

    private TopicBasedRepository<UserRoleData> getRepository(KafkaCluster kafkaCluster) {
        return kafkaCluster.getRepository(TOPIC_NAME, UserRoleData.class);
    }
}
