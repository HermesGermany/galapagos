package com.hermesworld.ais.galapagos.kafka.impl;

import com.hermesworld.ais.galapagos.kafka.KafkaClusterAdminClient;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.config.ConfigResource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultKafkaClusterAdminClient implements KafkaClusterAdminClient {

    private final Admin admin;

    public DefaultKafkaClusterAdminClient(Admin admin) {
        this.admin = admin;
    }

    @Override
    public KafkaFuture<Collection<AclBinding>> deleteAcls(Collection<AclBindingFilter> filters) {
        return admin.deleteAcls(filters).all();
    }

    @Override
    public KafkaFuture<Void> createAcls(Collection<AclBinding> bindings) {
        return admin.createAcls(bindings).all();
    }

    @Override
    public KafkaFuture<Collection<AclBinding>> describeAcls(AclBindingFilter filter) {
        return admin.describeAcls(filter).values();
    }

    @Override
    public KafkaFuture<Void> createTopic(NewTopic topic) {
        return admin.createTopics(Set.of(topic)).all();
    }

    @Override
    public KafkaFuture<Void> deleteTopic(String topicName) {
        return admin.deleteTopics(Set.of(topicName)).all();
    }

    @Override
    public KafkaFuture<Config> describeConfigs(ConfigResource resource) {
        return admin.describeConfigs(Set.of(resource)).values().getOrDefault(resource,
                KafkaFuture.completedFuture(new Config(Set.of())));
    }

    @Override
    public KafkaFuture<Collection<Node>> describeCluster() {
        return admin.describeCluster().nodes();
    }

    @Override
    public KafkaFuture<TopicDescription> describeTopic(String topicName) {
        return admin.describeTopics(Set.of(topicName)).topicNameValues().get(topicName);
    }

    @Override
    public KafkaFuture<Void> incrementalAlterConfigs(ConfigResource resource, Map<String, String> configValues) {
        List<AlterConfigOp> alterOps = configValues.entrySet().stream().map(entry -> {
            if (entry.getValue() == null) {
                return new AlterConfigOp(new ConfigEntry(entry.getKey(), null), AlterConfigOp.OpType.DELETE);
            }
            return new AlterConfigOp(new ConfigEntry(entry.getKey(), entry.getValue()), AlterConfigOp.OpType.SET);
        }).toList();

        return admin.incrementalAlterConfigs(Map.of(resource, alterOps)).all();
    }
}
