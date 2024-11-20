package com.hermesworld.ais.galapagos.security.roles.impl;

import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.InitPerCluster;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.security.roles.Role;
import com.hermesworld.ais.galapagos.security.roles.UserRoleData;
import com.hermesworld.ais.galapagos.security.roles.UserRoleService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class UserRoleServiceImpl implements UserRoleService, InitPerCluster {

    private final KafkaClusters kafkaClusters;

    public UserRoleServiceImpl(KafkaClusters kafkaClusters) {
        this.kafkaClusters = kafkaClusters;
    }

    public void init(KafkaCluster cluster) {
        getRepository(cluster).getObjects();
    }

    @Override
    public CompletableFuture<Void> addUserRole(String environmentId, String userName, Role role, String applicationId) {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return CompletableFuture.completedFuture(null);
        }
        UserRoleData data = new UserRoleData();
        data.setId(UUID.randomUUID().toString());
        data.setUserName(userName);
        data.setRole(role);
        data.setApplicationId(applicationId);
        return getRepository(kafkaCluster).save(data);
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
        return getRepository(kafkaCluster).getObject(userName).stream()
                .filter(role -> role.getUserName().equals(userName)).collect(Collectors.toList());
    }

    @Override
    public CompletableFuture<Void> deleteUserRole(String environmentId, UserRoleData value) {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return CompletableFuture.completedFuture(null);
        }
        return getRepository(kafkaCluster).delete(value);
    }

    private TopicBasedRepository<UserRoleData> getRepository(KafkaCluster kafkaCluster) {
        return kafkaCluster.getRepository("user-roles", UserRoleData.class);
    }
}
