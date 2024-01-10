package com.hermesworld.ais.galapagos.kafka;

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
 * Galapagos Interface for abstracting the not-so-helpful Kafka Admin interface. This allows for wrapping and e.g.
 * KafkaFuture encapsulation. <br>
 * Also, it is reduced to the relevant Kafka Admin operations for Galapagos.
 */
public interface KafkaClusterAdminClient {

    KafkaFuture<Collection<AclBinding>> deleteAcls(Collection<AclBindingFilter> filters);

    KafkaFuture<Void> createAcls(Collection<AclBinding> bindings);

    KafkaFuture<Collection<AclBinding>> describeAcls(AclBindingFilter filter);

    KafkaFuture<Void> createTopic(NewTopic topic);

    KafkaFuture<Void> deleteTopic(String topicName);

    KafkaFuture<Config> describeConfigs(ConfigResource resource);

    KafkaFuture<Collection<Node>> describeCluster();

    KafkaFuture<Void> incrementalAlterConfigs(ConfigResource resource, Map<String, String> configValues);

    KafkaFuture<TopicDescription> describeTopic(String topicName);

}
