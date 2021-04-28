package com.hermesworld.ais.galapagos.kafka.auth;

import org.json.JSONObject;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Interface of modules being able to provide (create, delete) authentication data (e.g. user name / password) for
 * access to a Kafka Cluster. Each {@link com.hermesworld.ais.galapagos.kafka.KafkaCluster} provides one authentication
 * module, based on its configuration.
 */
public interface KafkaAuthenticationModule {

    CompletableFuture<Void> init();

    CompletableFuture<CreateAuthenticationResult> createApplicationAuthentication(String applicationId,
            String applicationNormalizedName, JSONObject createParameters);

    CompletableFuture<Void> deleteApplicationAuthentication(String applicationId, JSONObject existingAuthData);

    void addRequiredKafkaProperties(Properties kafkaProperties);

}
