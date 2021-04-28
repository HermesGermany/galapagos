package com.hermesworld.ais.galapagos.kafka;

import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.util.HasKey;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public interface KafkaClusters {

    List<KafkaEnvironmentConfig> getEnvironmentsMetadata();

    Optional<KafkaEnvironmentConfig> getEnvironmentMetadata(String environmentId);

    List<String> getEnvironmentIds();

    String getProductionEnvironmentId();

    Optional<KafkaCluster> getEnvironment(String environmentId);

    /**
     * Returns all known environments as a List. Each environment can be queried for its ID.
     *
     * @return All known environments as an unmodifiable list.
     */
    default List<KafkaCluster> getEnvironments() {
        return getEnvironmentIds().stream().map(id -> getEnvironment(id).orElse(null)).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    Optional<KafkaAuthenticationModule> getAuthenticationModule(String environmentId);

    /**
     * Returns a repository for saving and retrieving objects in a Kafka Topic. Repositories returned by this method can
     * be used to store information for use across all clusters (best example is the repository for Application Owner
     * Requests). Usually, this repository will be stored in the production Kafka cluster, although clients should not
     * rely on this and leave the exact storage location up to the implementation.
     *
     * @param <T>        Type of the objects to store in the repository.
     * @param topicName  Name of the repository. It will be prefixed with a globally configured prefix for determining
     *                   the "real" Kafka Topic name.
     * @param valueClass Class of the objects to store in the repository. Instances of the class must be serializable as
     *                   JSON.
     * @return A repository for saving and retrieving objects with a "global", i.e. cross-cluster, scope.
     */
    <T extends HasKey> TopicBasedRepository<T> getGlobalRepository(String topicName, Class<T> valueClass);

    /**
     * Closes all connections to all Kafka clusters and releases all resources, e.g. Thread pools and executors.
     */
    void dispose();

}
