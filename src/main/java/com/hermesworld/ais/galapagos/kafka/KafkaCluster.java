package com.hermesworld.ais.galapagos.kafka;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.util.HasKey;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.acl.AclBinding;

/**
 * Representation of a live Kafka Cluster, i.e. a set of Kafka Brokers behaving as a single Kafka system. <br>
 * This interface maps all "low-level" KafkaAdmin operations to higher-level calls, and provides arbitrary persistence
 * based on internal topics. It does not apply rules or business logic to the operations, and does not check access
 * rights. <br>
 * A Kafka Cluster also fires events which can be caught by listeners e.g. for doing auditing logs or other
 * cross-cutting operations. See <code>events</code> package for possible listener interfaces to implement.
 *
 * @author AlbrechtFlo
 *
 */
public interface KafkaCluster {

	String getId();

	CompletableFuture<Void> updateUserAcls(KafkaUser user);

	CompletableFuture<Void> removeUserAcls(KafkaUser user);

	CompletableFuture<Void> visitAcls(Function<AclBinding, Boolean> callback);

	<T extends HasKey> TopicBasedRepository<T> getRepository(String topicName, Class<T> valueClass);

	Collection<TopicBasedRepository<?>> getRepositories();

	CompletableFuture<Void> createTopic(String topicName, TopicCreateParams topicCreateParams);

	CompletableFuture<Void> deleteTopic(String topicName);

	CompletableFuture<Set<TopicConfigEntry>> getTopicConfig(String topicName);

	CompletableFuture<Map<String, String>> getDefaultTopicConfig();

	CompletableFuture<Void> setTopicConfig(String topicName, Map<String, String> configValues);

	CompletableFuture<Integer> getActiveBrokerCount();

	CompletableFuture<TopicCreateParams> buildTopicCreateParams(String topicName);

	CompletableFuture<List<ConsumerRecord<String, String>>> peekTopicData(String topicName, int limit);
	
	CompletableFuture<String> getKafkaServerVersion();
}
