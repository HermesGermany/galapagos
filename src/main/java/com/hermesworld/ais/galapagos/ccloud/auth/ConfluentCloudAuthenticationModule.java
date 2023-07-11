package com.hermesworld.ais.galapagos.ccloud.auth;

import com.hermesworld.ais.galapagos.ccloud.apiclient.ApiKeySpec;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ConfluentCloudApiClient;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ServiceAccountSpec;
import com.hermesworld.ais.galapagos.kafka.auth.CreateAuthenticationResult;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Slf4j
public class ConfluentCloudAuthenticationModule implements KafkaAuthenticationModule {

    private final static String APP_SERVICE_ACCOUNT_DESC = "APP_{0}";

    private final static String DEVELOPER_SERVICE_ACCOUNT_DESC = "DEV_{0}";

    private final static String API_KEY_DESC = "Application {0}";

    private final static String API_DEVELOPER_KEY_DESC = "Developer {0}";

    final static String JSON_API_KEY = "apiKey";

    private final static String JSON_ISSUED_AT = "issuedAt";

    private final static String JSON_USER_ID = "userId";

    final static String JSON_EXPIRES_AT = "expiresAt";

    private final static String JSON_NUMERIC_ID = "numericId";

    private final static String NUMERIC_ID_PATTERN = "\\d{5,7}";

    private final ConfluentCloudApiClient client;

    private final ConfluentCloudAuthConfig config;

    private final Map<String, String> serviceAccountNumericIds = new ConcurrentHashMap<>();

    public ConfluentCloudAuthenticationModule(ConfluentCloudAuthConfig config) {
        this.client = new ConfluentCloudApiClient(config.getOrganizationApiKey(), config.getOrganizationApiSecret(),
                config.isServiceAccountIdCompatMode());
        this.config = config;
        try {
            this.getDeveloperApiKeyValidity();
        }
        catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid date for developer API key validity: " + config.getDeveloperApiKeyValidity(), e);
        }
    }

    @Override
    public CompletableFuture<Void> init() {
        if (config.isServiceAccountIdCompatMode()) {
            return getServiceAccountNumericIds().thenApply(o -> null);
        }
        return FutureUtil.noop();
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
        log.info("Creating API Key for application (normalized name) {}", applicationNormalizedName);

        // reset internal ID cache
        serviceAccountNumericIds.clear();

        String shortenedAppName = applicationNormalizedName.substring(0,
                Math.min(50, applicationNormalizedName.length()));

        return findServiceAccountForApp(applicationId)
                .thenCompose(account -> account.map(a -> CompletableFuture.completedFuture(a))
                        .orElseGet(() -> client.createServiceAccount("application-" + shortenedAppName,
                                appServiceAccountDescription(applicationId)).toFuture()))
                .thenCompose(account -> client.createApiKey(config.getEnvironmentId(), config.getClusterId(),
                        apiKeyDesc, account.getResourceId()).toFuture())
                .thenCompose(keyInfo -> toCreateAuthResult(keyInfo, null));
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
        if (StringUtils.hasLength(apiKey)) {
            log.info("Deleting API Key {}", apiKey);
            return client.listClusterApiKeys(config.getClusterId()).toFuture()
                    .thenCompose(ls -> ls.stream().filter(info -> apiKey.equals(info.getId())).findAny()
                            .map(info -> client.deleteApiKey(info).toFuture().thenApply(o -> (Void) null))
                            .orElse(FutureUtil.noop()));
        }

        return FutureUtil.noop();
    }

    @Override
    public String extractKafkaUserName(JSONObject existingAuthData) throws JSONException {
        String userId = existingAuthData.optString(JSON_USER_ID);
        if (!StringUtils.hasLength(userId)) {
            throw new JSONException("No userId set in application authentication data");
        }

        // special treatment for Confluent resourceId <-> numericId problem
        if (config.isServiceAccountIdCompatMode()) {
            // case 1: already stored (Created by Galapagos 2.6.0 or later, or executed admin job
            // update-confluent-auth-metadata)
            String numericId = existingAuthData.optString(JSON_NUMERIC_ID);
            if (StringUtils.hasLength(numericId)) {
                return "User:" + numericId;
            }

            // case 2: stored user ID is numeric
            if (userId.matches(NUMERIC_ID_PATTERN)) {
                return "User:" + userId;
            }

            // worst case: lookup ID synchronous (bah!)
            try {
                Map<String, String> numericIdMap = getServiceAccountNumericIds().get();
                if (numericIdMap.containsKey(userId)) {
                    return "User:" + numericIdMap.get(userId);
                }
                log.warn(
                        "Could not determine internal ID for service account {}, will return most likely invalid Kafka user name",
                        userId);
                return "User:" + userId;
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            catch (ExecutionException e) {
                log.error(
                        "Could not retrieve service account internal ID map, will return most likely invalid Kafka user name",
                        e);
                return "User:" + userId;
            }
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

        // reset internal ID cache
        serviceAccountNumericIds.clear();

        return findServiceAccountForDev(userName)
                .thenCompose(
                        account -> account.map(a -> CompletableFuture.completedFuture(a))
                                .orElseGet(() -> client.createServiceAccount("developer-" + finalUserName,
                                        devServiceAccountDescription(userName)).toFuture())
                                .thenCompose(acc -> client.createApiKey(config.getEnvironmentId(),
                                        config.getClusterId(), apiKeyDesc, acc.getResourceId()).toFuture())
                                .thenCompose(keyInfo -> toCreateAuthResult(keyInfo, expiresAt)));
    }

    @Override
    public CompletableFuture<Void> deleteDeveloperAuthentication(String userName, JSONObject existingAuthData) {
        return deleteApplicationAuthentication(null, existingAuthData);
    }

    @Override
    public Optional<Instant> extractExpiryDate(JSONObject authData) {
        if (authData.has(JSON_EXPIRES_AT)) {
            return Optional.of(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(authData.getString(JSON_EXPIRES_AT))));
        }
        return Optional.empty();
    }

    public boolean supportsDeveloperApiKeys() {
        return getDeveloperApiKeyValidity().isPresent();
    }

    /**
     * Upgrades a given "old" authentication metadata object. Is used by admin job "update-confluent-auth-metadata" to
     * e.g. add numeric IDs and Service Account Resource IDs.
     *
     * @param oldAuthMetadata Potentially "old" authentication metadata (function determines if update is required).
     * @return The "updated" metadata, or the unchanged metadata if already filled with new fields. As a future as an
     *         API call may be required to determine required information (e.g. internal numeric ID for a service
     *         account). The future can fail if the API call fails.
     */
    public CompletableFuture<JSONObject> upgradeAuthMetadata(JSONObject oldAuthMetadata) {
        String userId = oldAuthMetadata.optString(JSON_USER_ID);
        if (!StringUtils.hasLength(userId)) {
            return CompletableFuture.completedFuture(oldAuthMetadata);
        }

        // numeric user ID means we have to retrieve service account ID
        if (userId.matches(NUMERIC_ID_PATTERN)) {
            return getServiceAccountNumericIds().thenApply(map -> {
                String resourceId = map.entrySet().stream().filter(e -> userId.equals(e.getValue())).findAny()
                        .map(e -> e.getKey()).orElse(null);
                if (resourceId == null) {
                    log.warn("Unable to determine Service Account resource ID for numeric ID {}", userId);
                    return oldAuthMetadata;
                }
                JSONObject newMetadata = new JSONObject(oldAuthMetadata.toString());
                newMetadata.put(JSON_USER_ID, resourceId);
                newMetadata.put(JSON_NUMERIC_ID, userId);
                return newMetadata;
            });
        }

        // for now, no other scenarios for upgrading are supported
        return CompletableFuture.completedFuture(oldAuthMetadata);
    }

    private CompletableFuture<Map<String, String>> getServiceAccountNumericIds() {
        if (this.serviceAccountNumericIds.isEmpty()) {
            return client.getServiceAccountInternalIds().map(map -> {
                this.serviceAccountNumericIds.putAll(map);
                return map;
            }).toFuture();
        }
        return CompletableFuture.completedFuture(serviceAccountNumericIds);
    }

    private CompletableFuture<Optional<ServiceAccountSpec>> findServiceAccountForApp(String applicationId) {
        String desc = appServiceAccountDescription(applicationId);
        return client.listServiceAccounts().toFuture()
                .thenApply(ls -> ls.stream().filter(acc -> desc.equals(acc.getDescription())).findAny());
    }

    private CompletableFuture<Optional<ServiceAccountSpec>> findServiceAccountForDev(String userName) {
        String desc = devServiceAccountDescription(userName);
        return client.listServiceAccounts().toFuture()
                .thenApply(ls -> ls.stream().filter(acc -> desc.equals(acc.getDescription())).findAny());
    }

    private String appServiceAccountDescription(String applicationId) {
        return MessageFormat.format(APP_SERVICE_ACCOUNT_DESC, applicationId);
    }

    private String devServiceAccountDescription(String userName) {
        return MessageFormat.format(DEVELOPER_SERVICE_ACCOUNT_DESC, userName);
    }

    private CompletableFuture<CreateAuthenticationResult> toCreateAuthResult(ApiKeySpec keyInfo, Instant expiresAt) {
        JSONObject info = new JSONObject();
        info.put(JSON_API_KEY, keyInfo.getId());
        info.put(JSON_USER_ID, String.valueOf(keyInfo.getServiceAccountId()));
        info.put(JSON_ISSUED_AT, keyInfo.getCreatedAt().toString());
        if (expiresAt != null) {
            info.put(JSON_EXPIRES_AT, expiresAt.toString());
        }

        if (config.isServiceAccountIdCompatMode()) {
            return getServiceAccountNumericIds().thenApply(map -> {
                info.put(JSON_NUMERIC_ID, map.get(keyInfo.getServiceAccountId()));
                return new CreateAuthenticationResult(info, keyInfo.getSecret().getBytes(StandardCharsets.UTF_8));
            });
        }

        return CompletableFuture.completedFuture(
                new CreateAuthenticationResult(info, keyInfo.getSecret().getBytes(StandardCharsets.UTF_8)));
    }

    private Optional<Duration> getDeveloperApiKeyValidity() {
        if (!StringUtils.hasLength(config.getDeveloperApiKeyValidity())) {
            return Optional.empty();
        }

        return Optional.of(Duration.parse(this.config.getDeveloperApiKeyValidity()));
    }

}
