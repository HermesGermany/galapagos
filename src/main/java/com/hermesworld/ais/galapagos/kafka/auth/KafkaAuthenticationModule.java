package com.hermesworld.ais.galapagos.kafka.auth;

import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.CheckReturnValue;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Interface of modules being able to provide (create, delete) authentication data (e.g. user name / password) for
 * access to a Kafka Cluster. Each {@link com.hermesworld.ais.galapagos.kafka.KafkaCluster} provides one authentication
 * module, based on its configuration.
 */
public interface KafkaAuthenticationModule {

    @CheckReturnValue
    CompletableFuture<Void> init();

    @CheckReturnValue
    CompletableFuture<CreateAuthenticationResult> createApplicationAuthentication(String applicationId,
            String applicationNormalizedName, JSONObject createParameters);

    @CheckReturnValue
    CompletableFuture<CreateAuthenticationResult> updateApplicationAuthentication(String applicationId,
            String applicationNormalizedName, JSONObject createParameters, JSONObject existingAuthData);

    @CheckReturnValue
    CompletableFuture<Void> deleteApplicationAuthentication(String applicationId, JSONObject existingAuthData);

    void addRequiredKafkaProperties(Properties kafkaProperties);

    /**
     * Returns the Kafka username which represents the given application or developer from the given authentication data
     * which have been created by this module. The return value <b>must</b> include the <code>User:</code> prefix.
     *
     * @param existingAuthData Authentication data stored for the application or developer, which have been created by
     *                         this module.
     *
     * @return The Kafka username for the given application or developer, never <code>null</code>.
     *
     * @throws JSONException If authentication data could not be parsed, or if the username could not be determined from
     *                       the authentication data.
     */
    String extractKafkaUserName(JSONObject existingAuthData) throws JSONException;

    Optional<Instant> extractExpiryDate(JSONObject existingAuthData) throws JSONException;

    @CheckReturnValue
    CompletableFuture<CreateAuthenticationResult> createDeveloperAuthentication(String userName,
            JSONObject createParams);

    @CheckReturnValue
    CompletableFuture<Void> deleteDeveloperAuthentication(String userName, JSONObject existingAuthData);

}
