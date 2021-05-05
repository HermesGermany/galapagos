package com.hermesworld.ais.galapagos.ccloud.auth;

import com.hermesworld.ais.galapagos.ccloud.apiclient.ApiKeyInfo;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ConfluentApiClient;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ServiceAccountInfo;
import com.hermesworld.ais.galapagos.kafka.auth.CreateAuthenticationResult;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class ConfluentCloudAuthenticationModule implements KafkaAuthenticationModule {

    private final static String APP_SERVICE_ACCOUNT_DESC = "APP_{0}";

    private final static String API_KEY_DESC = "Application {0}";

    private final static String JSON_API_KEY = "apiKey";

    private final static String JSON_ISSUED_AT = "issuedAt";

    private final static String JSON_USER_ID = "userId";

    private final ConfluentApiClient client;

    private final ConfluentCloudAuthConfig config;

    public ConfluentCloudAuthenticationModule(ConfluentCloudAuthConfig config) {
        this.client = new ConfluentApiClient();
        this.config = config;
    }

    @Override
    public CompletableFuture<Void> init() {
        return client.login(config.getCloudUserName(), config.getCloudPassword()).toFuture().thenApply(o -> null);
    }

    @Override
    public void addRequiredKafkaProperties(Properties kafkaProperties) {
        kafkaProperties.setProperty("sasl.mechanism", "PLAIN");
        kafkaProperties.setProperty("sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule   required username='"
                        + config.getClusterApiKey() + "'   password='" + config.getClusterApiSecret() + "';");
        kafkaProperties.setProperty("security.protocol", "SASL_SSL");
    }

    @Override
    public CompletableFuture<CreateAuthenticationResult> createApplicationAuthentication(String applicationId,
            String applicationNormalizedName, JSONObject createParams) {
        String apiKeyDesc = MessageFormat.format(API_KEY_DESC, applicationNormalizedName);

        return findServiceAccountForApp(applicationId)
                .thenCompose(account -> account.map(a -> CompletableFuture.completedFuture(a))
                        .orElseGet(() -> client.createServiceAccount("application-" + applicationNormalizedName,
                                appServiceAccountDescription(applicationId)).toFuture()))
                .thenCompose(account -> client
                        .createApiKey(config.getEnvironmentId(), config.getClusterId(), apiKeyDesc, account.getId())
                        .toFuture())
                .thenApply(keyInfo -> toCreateAuthResult(keyInfo));
    }

    @Override
    public CompletableFuture<CreateAuthenticationResult> updateApplicationAuthentication(String applicationId,
            String applicationNormalizedName, JSONObject createParameters, JSONObject existingAuthData) {
        return deleteApplicationAuthentication(applicationId, existingAuthData).thenCompose(
                o -> createApplicationAuthentication(applicationId, applicationNormalizedName, createParameters));
    }

    @Override
    public CompletableFuture<Void> deleteApplicationAuthentication(String applicationId, JSONObject existingAuthData) {
        String apiKey = existingAuthData.optString(JSON_API_KEY);
        if (!StringUtils.isEmpty(apiKey)) {
            return client.listApiKeys(config.getEnvironmentId(), config.getClusterId()).toFuture()
                    .thenCompose(ls -> ls.stream().filter(info -> apiKey.equals(info.getKey())).findAny()
                            .map(info -> client.deleteApiKey(info).toFuture())
                            .orElse(CompletableFuture.completedFuture(true)))
                    .thenApply(o -> null);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String extractKafkaUserName(String applicationId, JSONObject existingAuthData) throws JSONException {
        String userId = existingAuthData.optString(JSON_USER_ID);
        if (StringUtils.isEmpty(userId)) {
            throw new JSONException("No userId set in application authentication data");
        }

        return "User:" + userId;
    }

    private CompletableFuture<Optional<ServiceAccountInfo>> findServiceAccountForApp(String applicationId) {
        String desc = appServiceAccountDescription(applicationId);
        return ensureClientLoggedIn().thenCompose(o -> client.listServiceAccounts().toFuture())
                .thenApply(ls -> ls.stream().filter(acc -> desc.equals(acc.getServiceDescription())).findAny());
    }

    private String appServiceAccountDescription(String applicationId) {
        return MessageFormat.format(APP_SERVICE_ACCOUNT_DESC, applicationId);
    }

    private CompletableFuture<Void> ensureClientLoggedIn() {
        if (client.isLoggedIn()) {
            return CompletableFuture.completedFuture(null);
        }

        return client.login(config.getCloudUserName(), config.getCloudPassword()).toFuture().thenApply(b -> null);
    }

    private CreateAuthenticationResult toCreateAuthResult(ApiKeyInfo keyInfo) {
        JSONObject info = new JSONObject();
        info.put(JSON_API_KEY, keyInfo.getKey());
        info.put(JSON_USER_ID, String.valueOf(keyInfo.getUserId()));
        info.put(JSON_ISSUED_AT, keyInfo.getCreated().toString());

        return new CreateAuthenticationResult(info, keyInfo.getSecret().getBytes(StandardCharsets.UTF_8));
    }

}
