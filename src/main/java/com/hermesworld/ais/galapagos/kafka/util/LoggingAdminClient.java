package com.hermesworld.ais.galapagos.kafka.util;

import com.hermesworld.ais.galapagos.kafka.KafkaClusterAdminClient;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
public class LoggingAdminClient implements KafkaClusterAdminClient {

    private final String clusterId;

    private final KafkaClusterAdminClient delegate;

    public LoggingAdminClient(String clusterId, KafkaClusterAdminClient delegate) {
        if (delegate instanceof LoggingAdminClient) {
            throw new IllegalArgumentException("Cannot create a logging Admin Client on a logging Admin Client");
        }
        this.clusterId = clusterId;
        this.delegate = delegate;
    }

    @Override
    public KafkaFuture<Collection<AclBinding>> deleteAcls(Collection<AclBindingFilter> filters) {
        return logOperation("deleteAcls", filters, () -> delegate.deleteAcls(filters));
    }

    @Override
    public KafkaFuture<Void> createAcls(Collection<AclBinding> bindings) {
        return logOperation("createAcls", bindings, () -> delegate.createAcls(bindings));
    }

    @Override
    public KafkaFuture<Collection<AclBinding>> describeAcls(AclBindingFilter filter) {
        return logOperation("describeAcls", filter, () -> delegate.describeAcls(filter));
    }

    @Override
    public KafkaFuture<Void> createTopic(NewTopic topic) {
        return logOperation("createTopic", topic, () -> delegate.createTopic(topic));
    }

    @Override
    public KafkaFuture<Void> deleteTopic(String topicName) {
        return logOperation("deleteTopic", topicName, () -> delegate.deleteTopic(topicName));
    }

    @Override
    public KafkaFuture<Config> describeConfigs(ConfigResource resource) {
        return logOperation("describeConfigs", resource, () -> delegate.describeConfigs(resource));
    }

    @Override
    public KafkaFuture<Collection<Node>> describeCluster() {
        return logOperation("describeCluster", "cluster", () -> delegate.describeCluster());
    }

    @Override
    public KafkaFuture<Void> incrementalAlterConfigs(ConfigResource resource, Map<String, String> configValues) {
        return logOperation("incrementalAlterConfigs", Set.of(resource, configValues),
                () -> delegate.incrementalAlterConfigs(resource, configValues));
    }

    @Override
    public KafkaFuture<TopicDescription> describeTopic(String topicName) {
        return logOperation("describeTopic", topicName, () -> delegate.describeTopic(topicName));
    }

    private <T> KafkaFuture<T> logOperation(String opText, Object logKey, Supplier<KafkaFuture<T>> future) {
        long startTime = System.currentTimeMillis();
        log.info("Kafka AdminClient Call on cluster {}: {} ({})", clusterId, opText, logKey);

        return future.get().whenComplete((v, t) -> logFutureComplete(opText, logKey, t, startTime));
    }

    private void logFutureComplete(String opText, Object logKey, Throwable error, long startTime) {
        long totalTime = System.currentTimeMillis() - startTime;
        if (error != null) {
            log.error("Kafka operation {} for {} FAILED after {} ms", opText, logKey, totalTime, error);
        }
        else {
            log.info("Kafka operation {} for {} COMPLETE after {} ms", opText, logKey, totalTime);
        }
    }
}
