package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.kafka.KafkaClusterAdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.config.ConfigResource;

import java.util.Collection;
import java.util.Map;

/**
 * Helper class used by admin jobs providing a "dry run". This class wraps an existing Kafka AdminClient and throws an
 * <code>UnsupportedOperationException</code> whenever a method is called which would modify something in the Kafka
 * cluster. Admin jobs subclass this class and override the calls they are interested in.
 */
public abstract class NoUpdatesAdminClient implements KafkaClusterAdminClient {

    private final KafkaClusterAdminClient delegate;

    public NoUpdatesAdminClient(KafkaClusterAdminClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public KafkaFuture<Void> createTopic(NewTopic topic) {
        throw new UnsupportedOperationException();
    }

    @Override
    public KafkaFuture<Void> deleteTopic(String topicName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public KafkaFuture<TopicDescription> describeTopic(String topicName) {
        return delegate.describeTopic(topicName);
    }

    @Override
    public KafkaFuture<Collection<Node>> describeCluster() {
        return delegate.describeCluster();
    }

    @Override
    public KafkaFuture<Collection<AclBinding>> describeAcls(AclBindingFilter filter) {
        return delegate.describeAcls(filter);
    }

    @Override
    public KafkaFuture<Void> createAcls(Collection<AclBinding> acls) {
        throw new UnsupportedOperationException();
    }

    @Override
    public KafkaFuture<Collection<AclBinding>> deleteAcls(Collection<AclBindingFilter> filters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public KafkaFuture<Config> describeConfigs(ConfigResource resource) {
        return delegate.describeConfigs(resource);
    }

    @Override
    public KafkaFuture<Void> incrementalAlterConfigs(ConfigResource resource, Map<String, String> configValues) {
        throw new UnsupportedOperationException();
    }
}
