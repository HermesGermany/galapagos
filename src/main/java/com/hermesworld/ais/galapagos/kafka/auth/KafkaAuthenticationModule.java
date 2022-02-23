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

    // TODO update doc
    /**
     * Returns the Kafka User Name which represents the given application, having the given authentication data which
     * have been created by this module. The return value <b>must</b> include the <code>User:</code> prefix.
     * 
     * @param existingAuthData Authentication data stored for the application, which have been created by this module.
     * 
     * @return The Kafka User Name for the given application and authentication data, never <code>null</code>.
     * 
     * @throws JSONException If the User Name could not be determined from the authentication data.
     */
    String extractKafkaUserName(JSONObject existingAuthData) throws JSONException;

    Optional<Instant> extractExpiryDate(JSONObject existingAuthData) throws JSONException;

    @CheckReturnValue
    CompletableFuture<CreateAuthenticationResult> createDeveloperAuthentication(String userName,
            JSONObject createParams);

    @CheckReturnValue
    CompletableFuture<Void> deleteDeveloperAuthentication(String userName, JSONObject existingAuthData);
}
