package com.hermesworld.ais.galapagos.kafka.impl;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.hermesworld.ais.galapagos.certificates.CaManager;
import com.hermesworld.ais.galapagos.kafka.KafkaSender;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Slf4j
class KafkaConnectionManager {

    private final Map<String, AdminClient> adminClients = new HashMap<>();

    private final Map<String, KafkaSenderImpl> senders = new HashMap<>();

    private final Map<String, KafkaConsumerFactory<String, String>> consumerFactories = new HashMap<>();

    private final String truststoreFile;

    private final String truststorePassword;

    private final KafkaFutureDecoupler futureDecoupler;

    public KafkaConnectionManager(List<KafkaEnvironmentConfig> environments, Map<String, CaManager> caManagers,
            String truststoreFile, String truststorePassword, KafkaFutureDecoupler futureDecoupler) {
        this.truststoreFile = truststoreFile;
        this.truststorePassword = truststorePassword;
        this.futureDecoupler = futureDecoupler;

        for (KafkaEnvironmentConfig env : environments) {
            String id = env.getId();
            log.debug("Creating Kafka Connections for " + id);
            CaManager caManager = caManagers.get(id);
            adminClients.put(id, buildAdminClient(env, caManager));
            senders.put(id, buildKafkaSender(env, caManager));
            consumerFactories.put(id, () -> buildConsumer(env, caManager));
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

    private AdminClient buildAdminClient(KafkaEnvironmentConfig environment, CaManager caManager) {
        return buildAdminClient(environment, caManager, new Properties());
    }

    private AdminClient buildAdminClient(KafkaEnvironmentConfig environment, CaManager caManager,
            Properties propsOverride) {
        Properties props = buildKafkaProperties(environment, caManager);
        props.putAll(propsOverride);

        return AdminClient.create(props);
    }

    private KafkaConsumer<String, String> buildConsumer(KafkaEnvironmentConfig environment, CaManager caManager) {
        Properties props = buildKafkaProperties(environment, caManager);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "galapagos." + UUID.randomUUID().toString());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // consumer_offset is irrelevant for us, as we use a new consumer group ID in every Galapagos instance
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "10000");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    private KafkaSenderImpl buildKafkaSender(KafkaEnvironmentConfig environment, CaManager caManager) {
        Properties props = buildKafkaProperties(environment, caManager);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "1"); // do not batch
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1");

        ProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(toMap(props));
        return new KafkaSenderImpl(new KafkaTemplate<>(factory), futureDecoupler);
    }

    private Properties buildKafkaProperties(KafkaEnvironmentConfig environment, CaManager caManager) {
        Properties props = new Properties();
        props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, environment.getBootstrapServers());
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        props.put(CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG, "60000");

        props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreFile);
        props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword);

        props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, caManager.getClientPkcs12File().getAbsolutePath());
        props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, caManager.getClientPkcs12Password());
        props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12");
        props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "");
        return props;
    }

    private static Map<String, Object> toMap(Properties props) {
        return props.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
    }
}
