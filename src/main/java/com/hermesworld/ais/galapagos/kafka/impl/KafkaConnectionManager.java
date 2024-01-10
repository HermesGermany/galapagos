package com.hermesworld.ais.galapagos.kafka.impl;

import com.hermesworld.ais.galapagos.kafka.KafkaSender;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
class KafkaConnectionManager {

    private final Map<String, AdminClient> adminClients = new HashMap<>();

    private final Map<String, KafkaSenderImpl> senders = new HashMap<>();

    private final Map<String, KafkaConsumerFactory<String, String>> consumerFactories = new HashMap<>();

    private final KafkaFutureDecoupler futureDecoupler;

    private final Long adminClientRequestTimeout;

    public KafkaConnectionManager(List<KafkaEnvironmentConfig> environments,
            Map<String, KafkaAuthenticationModule> authenticationModules, KafkaFutureDecoupler futureDecoupler,
            Long adminClientRequestTimeout) {
        this.futureDecoupler = futureDecoupler;
        this.adminClientRequestTimeout = adminClientRequestTimeout;

        for (KafkaEnvironmentConfig env : environments) {
            String id = env.getId();
            log.debug("Creating Kafka Connections for " + id);
            KafkaAuthenticationModule authModule = authenticationModules.get(id);
            adminClients.put(id, buildAdminClient(env, authModule));
            senders.put(id, buildKafkaSender(env, authModule));
            consumerFactories.put(id, () -> buildConsumer(env, authModule));
        }
    }

    public void dispose() {
        adminClients.values().forEach(Admin::close);
        adminClients.clear();

        // Senders have no resources to close here
        senders.clear();
    }

    public Set<String> getEnvironmentIds() {
        return Collections.unmodifiableSet(adminClients.keySet());
    }

    public CompletableFuture<Map<String, Boolean>> getBrokerOnlineState(String environmentId) {
        AdminClient client = adminClients.get(environmentId);
        if (client == null) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        // TODO implement
        return CompletableFuture.completedFuture(Collections.emptyMap());
    }

    public AdminClient getAdminClient(String environmentId) {
        return adminClients.get(environmentId);
    }

    public KafkaSender getKafkaSender(String environmentId) {
        return senders.get(environmentId);
    }

    public KafkaConsumerFactory<String, String> getConsumerFactory(String environmentId) {
        return consumerFactories.get(environmentId);
    }

    private AdminClient buildAdminClient(KafkaEnvironmentConfig environment,
            KafkaAuthenticationModule authenticationModule) {
        Properties props = buildKafkaProperties(environment, authenticationModule);
        if (adminClientRequestTimeout != null) {
            props.setProperty(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(adminClientRequestTimeout));
            props.setProperty(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG,
                    String.valueOf(adminClientRequestTimeout));
            props.setProperty(CommonClientConfigs.CONNECTIONS_MAX_IDLE_MS_CONFIG,
                    String.valueOf(adminClientRequestTimeout));
        }
        return AdminClient.create(props);
    }

    private KafkaConsumer<String, String> buildConsumer(KafkaEnvironmentConfig environment,
            KafkaAuthenticationModule authenticationModule) {
        Properties props = buildKafkaProperties(environment, authenticationModule);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "galapagos." + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // consumer_offset is irrelevant for us, as we use a new consumer group ID in every Galapagos instance
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "10000");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    private KafkaSenderImpl buildKafkaSender(KafkaEnvironmentConfig environment,
            KafkaAuthenticationModule authenticationModule) {
        Properties props = buildKafkaProperties(environment, authenticationModule);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "1"); // do not batch
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1");

        ProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(toMap(props));
        return new KafkaSenderImpl(new KafkaTemplate<>(factory), futureDecoupler);
    }

    private Properties buildKafkaProperties(KafkaEnvironmentConfig environment,
            KafkaAuthenticationModule authenticationModule) {
        Properties props = new Properties();
        props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, environment.getBootstrapServers());
        props.put(CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG, "60000");

        authenticationModule.addRequiredKafkaProperties(props);
        return props;
    }

    private static Map<String, Object> toMap(Properties props) {
        return props.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
    }
}
