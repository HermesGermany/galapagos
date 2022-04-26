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
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class ConfluentCloudAuthenticationModule implements KafkaAuthenticationModule {

    private final static String APP_SERVICE_ACCOUNT_DESC = "APP_{0}";

    private final static String DEVELOPER_SERVICE_ACCOUNT_DESC = "DEV_{0}";

    private final static String API_KEY_DESC = "Application {0}";

    private final static String API_DEVELOPER_KEY_DESC = "Developer {0}";

    private final static String JSON_API_KEY = "apiKey";

    private final static String JSON_ISSUED_AT = "issuedAt";

    private final static String JSON_USER_ID = "userId";

    private final static String EXPIRES_AT = "expiresAt";

    private final ConfluentApiClient client;

    private final ConfluentCloudAuthConfig config;

    public ConfluentCloudAuthenticationModule(ConfluentCloudAuthConfig config) {
        this.client = new ConfluentApiClient();
        this.config = config;
        try {
            this.getDeveloperApiKeyValidity();
        }
        catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date for developer API key validity", e);
        }
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
                .thenApply(keyInfo -> toCreateAuthResult(keyInfo, null));
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
            return ensureClientLoggedIn()
                    .thenCompose(o -> client.listApiKeys(config.getEnvironmentId(), config.getClusterId()).toFuture())
                    .thenCompose(ls -> ls.stream().filter(info -> apiKey.equals(info.getKey())).findAny()
                            .map(info -> client.deleteApiKey(info).toFuture())
                            .orElse(CompletableFuture.completedFuture(true)))
                    .thenApply(o -> null);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String extractKafkaUserName(JSONObject existingAuthData) throws JSONException {
        String userId = existingAuthData.optString(JSON_USER_ID);
        if (StringUtils.isEmpty(userId)) {
            throw new JSONException("No userId set in application authentication data");
        }

        return "User:" + userId;
    }

    @Override
    public CompletableFuture<CreateAuthenticationResult> createDeveloperAuthentication(String userName,
            JSONObject createParams) {
        if (!supportsDeveloperApiKeys()) {
            return CompletableFuture
                    .failedFuture(new IllegalStateException("Developer API Keys not enabled on this Environment"));
        }
        String apiKeyDesc = MessageFormat.format(API_DEVELOPER_KEY_DESC, userName);
        Duration validity = getDeveloperApiKeyValidity().orElseThrow();
        Instant expiresAt = Instant.now().plus(validity);
        String finalUserName = userName.split("@")[0];
        return findServiceAccountForDev(userName)
                .thenCompose(
                        account -> account.map(a -> CompletableFuture.completedFuture(a))
                                .orElseGet(() -> client.createServiceAccount("developer-" + finalUserName,
                                        devServiceAccountDescription(userName)).toFuture()))
                .thenCompose(account -> client
                        .createApiKey(config.getEnvironmentId(), config.getClusterId(), apiKeyDesc, account.getId())
                        .toFuture())
                .thenApply(keyInfo -> toCreateAuthResult(keyInfo, expiresAt));
    }

    @Override
    public CompletableFuture<Void> deleteDeveloperAuthentication(String userName, JSONObject existingAuthData) {
        return deleteApplicationAuthentication(null, existingAuthData);
    }

    @Override
    public Optional<Instant> extractExpiryDate(JSONObject authData) {
        if (authData.has(EXPIRES_AT)) {
            return Optional.of(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(authData.getString(EXPIRES_AT))));
        }
        return Optional.empty();
    }

    private CompletableFuture<Optional<ServiceAccountInfo>> findServiceAccountForApp(String applicationId) {
        String desc = appServiceAccountDescription(applicationId);
        return ensureClientLoggedIn().thenCompose(o -> client.listServiceAccounts().toFuture())
                .thenApply(ls -> ls.stream().filter(acc -> desc.equals(acc.getServiceDescription())).findAny());
    }

    private CompletableFuture<Optional<ServiceAccountInfo>> findServiceAccountForDev(String userName) {
        String desc = devServiceAccountDescription(userName);
        return ensureClientLoggedIn().thenCompose(o -> client.listServiceAccounts().toFuture())
                .thenApply(ls -> ls.stream().filter(acc -> desc.equals(acc.getServiceDescription())).findAny());
    }

    private String appServiceAccountDescription(String applicationId) {
        return MessageFormat.format(APP_SERVICE_ACCOUNT_DESC, applicationId);
    }

    private String devServiceAccountDescription(String userName) {
        return MessageFormat.format(DEVELOPER_SERVICE_ACCOUNT_DESC, userName);
    }

    private CompletableFuture<Void> ensureClientLoggedIn() {
        if (client.isLoggedIn()) {
            return CompletableFuture.completedFuture(null);
        }

        return client.login(config.getCloudUserName(), config.getCloudPassword()).toFuture().thenApply(b -> null);
    }

    private CreateAuthenticationResult toCreateAuthResult(ApiKeyInfo keyInfo, Instant expiresAt) {
        JSONObject info = new JSONObject();
        info.put(JSON_API_KEY, keyInfo.getKey());
        info.put(JSON_USER_ID, String.valueOf(keyInfo.getUserId()));
        info.put(JSON_ISSUED_AT, keyInfo.getCreated().toString());

        if (expiresAt != null) {
            info.put(EXPIRES_AT, expiresAt.toString());
        }

        return new CreateAuthenticationResult(info, keyInfo.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public boolean supportsDeveloperApiKeys() {
        return getDeveloperApiKeyValidity().isPresent();
    }

    private Optional<Duration> getDeveloperApiKeyValidity() {
        if (StringUtils.isEmpty(config.getDeveloperApiKeyValidity())) {
            return Optional.empty();
        }

        return Optional.of(Duration.parse(this.config.getDeveloperApiKeyValidity()));
    }

}
