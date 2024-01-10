package com.hermesworld.ais.galapagos.kafka.impl;

import com.hermesworld.ais.galapagos.kafka.*;
import com.hermesworld.ais.galapagos.kafka.util.KafkaTopicConfigHelper;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.acl.*;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.ConfigResource.Type;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.apache.kafka.common.resource.ResourceType;
import org.springframework.util.ObjectUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class ConnectedKafkaCluster implements KafkaCluster {

    private final String environmentId;

    private KafkaClusterAdminClient adminClient;

    private final KafkaRepositoryContainer repositoryContainer;

    private final Map<String, TopicBasedRepository<?>> repositories = new ConcurrentHashMap<>();

    private final KafkaConsumerFactory<String, String> kafkaConsumerFactory;

    private final KafkaFutureDecoupler futureDecoupler;

    private static final long MAX_POLL_TIME = Duration.ofSeconds(10).toMillis();

    public ConnectedKafkaCluster(String environmentId, KafkaRepositoryContainer repositoryContainer,
            KafkaClusterAdminClient adminClient, KafkaConsumerFactory<String, String> kafkaConsumerFactory,
            KafkaFutureDecoupler futureDecoupler) {
        this.environmentId = environmentId;
        this.adminClient = adminClient;
        this.repositoryContainer = repositoryContainer;
        this.kafkaConsumerFactory = kafkaConsumerFactory;
        this.futureDecoupler = futureDecoupler;
    }

    /**
     * Convenience function to enable wrapping of the contained AdminClient, e.g. to intercept update calls within a
     * "dry-run" operation.
     *
     * @param wrapperFn Function returning a new AdminClient object which should wrap the existing AdminClient (passed
     *                  to the function). It is also valid to return the AdminClient object passed to this function.
     */
    public void wrapAdminClient(Function<KafkaClusterAdminClient, KafkaClusterAdminClient> wrapperFn) {
        this.adminClient = wrapperFn.apply(this.adminClient);
    }

    @Override
    public String getId() {
        return environmentId;
    }

    @Override
    public CompletableFuture<Void> updateUserAcls(KafkaUser user) {
        List<AclBinding> createAcls = new ArrayList<>();

        return getUserAcls(user.getKafkaUserName()).thenCompose(acls -> {
            List<AclBinding> targetAcls = new ArrayList<>(user.getRequiredAclBindings());

            List<AclBinding> deleteAcls = new ArrayList<>(acls);

            createAcls.addAll(targetAcls);
            createAcls.removeAll(acls);
            deleteAcls.removeAll(targetAcls);

            return deleteAcls.isEmpty() ? CompletableFuture.completedFuture(null)
                    : toCompletableFuture(adminClient
                            .deleteAcls(deleteAcls.stream().map(acl -> acl.toFilter()).collect(Collectors.toList())));
        }).thenCompose(o -> createAcls.isEmpty() ? CompletableFuture.completedFuture(null)
                : toCompletableFuture(adminClient.createAcls(createAcls)));
    }

    @Override
    public CompletableFuture<Void> removeUserAcls(KafkaUser user) {
        String userName = user.getKafkaUserName();
        if (userName == null) {
            return FutureUtil.noop();
        }
        return toCompletableFuture(adminClient.deleteAcls(List.of(userAclFilter(userName, ResourceType.ANY))))
                .thenApply(o -> null);
    }

    @Override
    public CompletableFuture<Void> visitAcls(Function<AclBinding, Boolean> callback) {
        return toCompletableFuture(adminClient.describeAcls(AclBindingFilter.ANY)).thenAccept(acls -> {
            for (AclBinding acl : acls) {
                if (!callback.apply(acl)) {
                    break;
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends HasKey> TopicBasedRepository<T> getRepository(String topicName, Class<T> valueClass) {
        return (TopicBasedRepository<T>) repositories.computeIfAbsent(topicName,
                s -> repositoryContainer.addRepository(topicName, valueClass));
    }

    @Override
    public Collection<TopicBasedRepository<?>> getRepositories() {
        return new HashSet<>(repositories.values());
    }

    @Override
    public CompletableFuture<Void> createTopic(String topicName, TopicCreateParams topicCreateParams) {
        NewTopic newTopic = new NewTopic(topicName, topicCreateParams.getNumberOfPartitions(),
                (short) topicCreateParams.getReplicationFactor()).configs(topicCreateParams.getTopicConfigs());

        return toCompletableFuture(this.adminClient.createTopic(newTopic));
    }

    @Override
    public CompletableFuture<Void> deleteTopic(String topicName) {
        AclBindingFilter aclFilter = new AclBindingFilter(
                new ResourcePatternFilter(ResourceType.TOPIC, topicName, PatternType.LITERAL),
                new AccessControlEntryFilter(null, null, AclOperation.ANY, AclPermissionType.ANY));

        KafkaFuture<Void> deleteTopicFuture = this.adminClient.deleteTopic(topicName);

        return toCompletableFuture(deleteTopicFuture)
                .thenCompose(o -> toCompletableFuture(adminClient.deleteAcls(Set.of(aclFilter)))).thenApply(o -> null);
    }

    @Override
    public CompletableFuture<Set<TopicConfigEntry>> getTopicConfig(String topicName) {
        ConfigResource cres = new ConfigResource(ConfigResource.Type.TOPIC, topicName);

        return toCompletableFuture(adminClient.describeConfigs(cres)).thenApply(config -> config.entries().stream()
                .map(entry -> new TopicConfigEntryImpl(entry)).collect(Collectors.toSet()));
    }

    @Override
    public CompletableFuture<Map<String, String>> getDefaultTopicConfig() {
        return toCompletableFuture(adminClient.describeCluster()).thenCompose(nodes -> {
            if (nodes.isEmpty()) {
                return CompletableFuture.failedFuture(new KafkaException("No nodes in cluster"));
            }
            return toCompletableFuture(adminClient.describeConfigs(
                    new ConfigResource(ConfigResource.Type.BROKER, String.valueOf(nodes.iterator().next().id()))));
        }).thenApply(config -> KafkaTopicConfigHelper.getTopicDefaultValues(config));
    }

    @Override
    public CompletableFuture<Void> setTopicConfig(String topicName, Map<String, String> configValues) {
        return toCompletableFuture(adminClient
                .incrementalAlterConfigs(new ConfigResource(ConfigResource.Type.TOPIC, topicName), configValues));
    }

    @Override
    public CompletableFuture<Integer> getActiveBrokerCount() {
        return toCompletableFuture(adminClient.describeCluster()).thenApply(nodes -> nodes.size());
    }

    @Override
    public CompletableFuture<TopicCreateParams> buildTopicCreateParams(String topicName) {
        return toCompletableFuture(adminClient.describeTopic(topicName))
                .thenCompose(desc -> buildCreateTopicParams(desc));
    }

    @Override
    public CompletableFuture<List<ConsumerRecord<String, String>>> peekTopicData(String topicName, int limit) {
        CompletableFuture<List<ConsumerRecord<String, String>>> result = new CompletableFuture<>();

        long startTime = System.currentTimeMillis();

        Runnable r = () -> {
            KafkaConsumer<String, String> consumer = kafkaConsumerFactory.newConsumer();
            consumer.subscribe(Set.of(topicName), new ConsumerRebalanceListener() {
                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                }

                // TODO do we really want to do this? This way, Galapagos could be mis-used as a continuous read tool
                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                    Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
                    for (Map.Entry<TopicPartition, Long> offset : endOffsets.entrySet()) {
                        consumer.seek(offset.getKey(), Math.max(0, offset.getValue() - limit));
                    }
                }
            });
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            while (!Thread.interrupted() && records.size() < limit
                    && System.currentTimeMillis() - startTime < MAX_POLL_TIME) {
                try {
                    ConsumerRecords<String, String> polled = consumer.poll(Duration.ofSeconds(1));
                    polled.forEach(rec -> {
                        if (records.size() < limit) {
                            records.add(rec);
                        }
                    });
                }
                catch (InterruptException | WakeupException e) {
                    break;
                }
                catch (KafkaException e) {
                    result.completeExceptionally(e);
                    try {
                        consumer.close();
                    }
                    catch (Throwable t) {
                    }
                    return;
                }
            }
            try {
                consumer.close();
            }
            catch (Throwable t) {
            }
            result.complete(records);
        };

        new Thread(r).start();
        return result;
    }

    @Override
    public CompletableFuture<String> getKafkaServerVersion() {
        Function<String, String> toVersionString = s -> !s.contains("-") ? s : s.substring(0, s.indexOf('-'));
        return toCompletableFuture(adminClient.describeCluster()).thenCompose(coll -> {
            String nodeName = coll.iterator().next().idString();

            return toCompletableFuture(adminClient.describeConfigs(new ConfigResource(Type.BROKER, nodeName)))
                    .thenApply(config -> config.get("inter.broker.protocol.version") == null ? "UNKNOWN_VERSION"
                            : config.get("inter.broker.protocol.version").value())
                    .thenApply(toVersionString);
        });
    }

    private CompletableFuture<TopicCreateParams> buildCreateTopicParams(TopicDescription description) {
        return getTopicConfig(description.name()).thenApply(configs -> {
            TopicCreateParams params = new TopicCreateParams(description.partitions().size(),
                    description.partitions().get(0).replicas().size());
            for (TopicConfigEntry config : configs) {
                if (!config.isDefault() && !config.isSensitive()) {
                    params.setTopicConfig(config.getName(), config.getValue());
                }
            }
            return params;
        });
    }

    private CompletableFuture<Collection<AclBinding>> getUserAcls(String username) {
        if (ObjectUtils.isEmpty(username)) {
            return CompletableFuture.completedFuture(List.of());
        }
        return toCompletableFuture(adminClient.describeAcls(userAclFilter(username, ResourceType.ANY)));
    }

    private AclBindingFilter userAclFilter(String username, ResourceType resourceType) {
        ResourcePatternFilter patternFilter = new ResourcePatternFilter(resourceType, null, PatternType.ANY);
        AccessControlEntryFilter entryFilter = new AccessControlEntryFilter(username, null, AclOperation.ANY,
                AclPermissionType.ANY);
        return new AclBindingFilter(patternFilter, entryFilter);
    }

    private <T> CompletableFuture<T> toCompletableFuture(KafkaFuture<T> kafkaFuture) {
        return futureDecoupler.toCompletableFuture(kafkaFuture);
    }

}
