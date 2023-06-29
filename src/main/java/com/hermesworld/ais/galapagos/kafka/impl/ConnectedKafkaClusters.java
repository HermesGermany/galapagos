package com.hermesworld.ais.galapagos.kafka.impl;

import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaExecutorFactory;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.util.HasKey;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ConnectedKafkaClusters implements KafkaClusters {

    private List<KafkaEnvironmentConfig> environmentMetadata;

    private final Map<String, ConnectedKafkaCluster> clusters = new ConcurrentHashMap<>();

    private final String productionEnvironmentId;

    private final Map<String, KafkaAuthenticationModule> authenticationModules;

    private final Set<KafkaRepositoryContainerImpl> repoContainers = new HashSet<>();

    private final KafkaConnectionManager connectionManager;

    public ConnectedKafkaClusters(List<KafkaEnvironmentConfig> environmentMetadata,
            Map<String, KafkaAuthenticationModule> authenticationModules, String productionEnvironmentId,
            String galapagosInternalPrefix, KafkaExecutorFactory executorFactory,
            int topicRepositoryReplicationFactor) {
        this.environmentMetadata = environmentMetadata;
        this.productionEnvironmentId = productionEnvironmentId;
        this.authenticationModules = authenticationModules;

        KafkaFutureDecoupler futureDecoupler = new KafkaFutureDecoupler(executorFactory);

        this.connectionManager = new KafkaConnectionManager(environmentMetadata, authenticationModules,
                futureDecoupler);

        for (KafkaEnvironmentConfig envMeta : environmentMetadata) {
            KafkaRepositoryContainerImpl repoContainer = new KafkaRepositoryContainerImpl(connectionManager,
                    envMeta.getId(), galapagosInternalPrefix, topicRepositoryReplicationFactor);
            ConnectedKafkaCluster cluster = buildConnectedKafkaCluster(envMeta.getId(), connectionManager,
                    repoContainer, futureDecoupler);
            clusters.put(envMeta.getId(), cluster);
            repoContainers.add(repoContainer);
        }
    }

    @Override
    public void dispose() {
        connectionManager.dispose();
        repoContainers.forEach(KafkaRepositoryContainerImpl::dispose);
        clusters.clear();
        environmentMetadata = Collections.emptyList();
    }

    @Override
    public List<KafkaEnvironmentConfig> getEnvironmentsMetadata() {
        return environmentMetadata;
    }

    @Override
    public Optional<KafkaEnvironmentConfig> getEnvironmentMetadata(String environmentId) {
        // TODO a map would be more optimized
        return environmentMetadata.stream().filter(env -> environmentId.equals(env.getId())).findFirst();
    }

    @Override
    public List<String> getEnvironmentIds() {
        return environmentMetadata.stream().map(KafkaEnvironmentConfig::getId).collect(Collectors.toList());
    }

    @Override
    public String getProductionEnvironmentId() {
        return productionEnvironmentId;
    }

    @Override
    public Optional<KafkaCluster> getEnvironment(String environmentId) {
        if (ObjectUtils.isEmpty(environmentId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(clusters.get(environmentId));
    }

    @Override
    public <T extends HasKey> TopicBasedRepository<T> getGlobalRepository(String topicName, Class<T> valueClass) {
        KafkaCluster cluster = getEnvironment(getProductionEnvironmentId()).orElse(null);
        if (cluster == null) {
            throw new RuntimeException("Internal error: No Kafka cluster instance for production environment found");
        }

        return cluster.getRepository(topicName, valueClass);
    }

    @Override
    public Optional<KafkaAuthenticationModule> getAuthenticationModule(String environmentId) {
        return Optional.ofNullable(authenticationModules.get(environmentId));
    }

    private static ConnectedKafkaCluster buildConnectedKafkaCluster(String environmentId,
            KafkaConnectionManager connectionManager, KafkaRepositoryContainer repositoryContainer,
            KafkaFutureDecoupler futureDecoupler) {
        return new ConnectedKafkaCluster(environmentId, repositoryContainer,
                connectionManager.getAdminClient(environmentId), connectionManager.getConsumerFactory(environmentId),
                futureDecoupler);
    }

}
