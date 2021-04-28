package com.hermesworld.ais.galapagos.kafka.impl;

import com.hermesworld.ais.galapagos.kafka.KafkaSender;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.ConfigResource.Type;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.WakeupException;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class KafkaRepositoryContainerImpl implements KafkaRepositoryContainer {

    private final KafkaConsumer<String, String> consumer;

    private final KafkaSender sender;

    private final Map<String, TopicBasedRepositoryImpl<?>> repositories = new ConcurrentHashMap<>();

    private final AtomicBoolean refreshSubscriptions = new AtomicBoolean();

    private final AdminClient adminClient;

    private final String environmentId;

    private static final Duration POLL_DURATION = Duration.of(10, ChronoUnit.SECONDS);

    private static final long CONSUMER_ERROR_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private Thread consumerThread;

    private final Object consumeSemaphore = new Object();

    private final String prefix;

    public KafkaRepositoryContainerImpl(KafkaConnectionManager connectionManager, String environmentId,
            String galapagosInternalPrefix) {
        this.consumer = connectionManager.getConsumerFactory(environmentId).newConsumer();
        this.sender = connectionManager.getKafkaSender(environmentId);
        this.adminClient = connectionManager.getAdminClient(environmentId);
        this.environmentId = environmentId;
        this.prefix = galapagosInternalPrefix;

        this.consumerThread = new Thread(this::consume);
        this.consumerThread.start();
    }

    public void dispose() {
        if (this.consumerThread != null) {
            this.consumer.wakeup();
            this.consumerThread = null;
        }
    }

    @Override
    public <T extends HasKey> TopicBasedRepository<T> addRepository(String topicName, Class<T> valueClass) {
        String kafkaTopicName = prefix + topicName;
        ensureTopicExists(kafkaTopicName);
        TopicBasedRepositoryImpl<T> repository = new TopicBasedRepositoryImpl<>(kafkaTopicName, topicName, valueClass,
                sender);
        this.repositories.put(kafkaTopicName, repository);
        refreshSubscriptions.set(true);
        synchronized (consumeSemaphore) {
            consumeSemaphore.notify();
        }

        return repository;
    }

    private void updateSubscriptions() {
        consumer.subscribe(repositories.keySet(), new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                log.debug("Environment " + environmentId + ": Consumer has been assigned to partitions " + partitions);
                consumer.seekToBeginning(partitions);
            }
        });
    }

    private void ensureTopicExists(String topic) {
        try {
            Map<String, TopicDescription> desc;

            try {
                desc = this.adminClient.describeTopics(Collections.singleton(topic)).all().get();
            }
            catch (Exception e) {
                desc = Collections.emptyMap();
            }

            if (desc.isEmpty()) {
                log.info("Creating metadata topic " + topic + " on environment " + environmentId);
                int nodeCount = this.adminClient.describeCluster().nodes().get().size();

                // short replicationFactor = (short) (nodeCount == 1 ? 1 : 2);
                // FIXME this must be configurable
                short replicationFactor = 3;

                this.adminClient.createTopics(Collections.singleton(new NewTopic(topic, 3, replicationFactor))).all()
                        .get();

                // use Log Compaction instead of Retention time
                ConfigResource res = new ConfigResource(Type.TOPIC, topic);
                List<ConfigEntry> configs = List
                        .of(new ConfigEntry(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT));

                Config config = new Config(configs);
                // We intentionally use alterConfigs() (and not incrementalAlterConfigs) to be pre-2.3 compatible
                // noinspection deprecation
                this.adminClient.alterConfigs(Collections.singletonMap(res, config)).all().get();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        }
    }

    private void consume() {
        while (repositories.isEmpty() && !Thread.interrupted()) {
            try {
                synchronized (consumeSemaphore) {
                    consumeSemaphore.wait();
                }
            }
            catch (InterruptedException e) {
                return;
            }
        }

        while (!Thread.interrupted()) {
            if (refreshSubscriptions.getAndSet(false)) {
                updateSubscriptions();
            }

            try {
                long start = 0;
                if (log.isTraceEnabled()) {
                    log.trace("Calling poll() on environment " + environmentId);
                    start = System.currentTimeMillis();
                }
                ConsumerRecords<String, String> records = consumer.poll(POLL_DURATION);
                if (log.isTraceEnabled()) {
                    log.trace("poll() returned " + records.count() + " record(s) and took "
                            + (System.currentTimeMillis() - start) + " ms");
                }
                for (ConsumerRecord<String, String> record : records) {
                    TopicBasedRepositoryImpl<?> repository = repositories.get(record.topic());
                    if (repository != null) {
                        repository.messageReceived(record.topic(), record.key(), record.value());
                    }
                    else {
                        log.warn("No handler found for message on topic " + record.topic());
                    }
                }
            }
            catch (WakeupException | InterruptException e) {
                // signal to close consumer!
                break;
            }
            catch (AuthenticationException | AuthorizationException e) {
                log.error("Unrecoverable exception when polling Kafka consumer, will exit consumer Thread", e);
                break;
            }
            catch (KafkaException e) {
                log.error("Exception when polling Kafka consumer, will retry in 30 seconds", e);
                try {
                    // noinspection BusyWait
                    Thread.sleep(CONSUMER_ERROR_WAIT_MILLIS);
                }
                catch (InterruptedException ie) {
                    break;
                }
            }
        }

        consumer.close();
    }

}
