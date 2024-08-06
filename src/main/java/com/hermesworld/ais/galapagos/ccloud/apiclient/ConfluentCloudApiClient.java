package com.hermesworld.ais.galapagos.ccloud.apiclient;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An API Client for accessing the Confluent Cloud REST API. That is, all relevant REST endpoints starting with
 * <code>https://api.confluent.cloud/</code>, but e.g. not the REST endpoints specific to a cluster. <br>
 * <br>
 * <b>Important note:</b> As of Sep 2022, only "internal" numeric IDs of service accounts can be used for setting ACLs
 * for service accounts via KafkaAdmin, although the REST API never reports these internal IDs. Confluent works on a
 * solution to enable usage of the "resource ID" of each service account for ACLs, but that is not yet ready (Confluent
 * internal ticket Kengine-24). <br>
 * Confluent offers an "inofficial" API endpoint for retrieving the internal ID for service accounts as a workaround. If
 * you set <code>idCompatMode</code> to <code>true</code>, this endpoint is additionally called for retrieving and the
 * internal ID for each service account, otherwise, the field <code>numericId</code> of {@link ServiceAccountSpec}
 * objects will always be <code>null</code>.
 */
@SuppressWarnings("JavadocLinkAsPlainText")
@Slf4j
public class ConfluentCloudApiClient {

    private static final String BASE_URL = "https://api.confluent.cloud";

    private final String baseUrl;

    private final WebClient client;

    private final boolean idCompatMode;

    /**
     * Creates a new Confluent Cloud REST API Client, using a given API Key and secret.
     *
     * @param apiKey       API Key to use for authentication.
     * @param apiSecret    Secret for the API Key for authentication.
     * @param idCompatMode If <code>true</code>, the "internal" numeric ID of service accounts is retrieved and used for
     *                     ACL related purposes. See description of this class for details.
     */
    public ConfluentCloudApiClient(String baseUrl, String apiKey, String apiSecret, boolean idCompatMode) {
        this.baseUrl = baseUrl;
        this.client = WebClient.builder().baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, buildAuth(apiKey, apiSecret)).build();
        this.idCompatMode = idCompatMode;
    }

    public ConfluentCloudApiClient(String apiKey, String apiSecret, boolean idCompatMode) {
        this(BASE_URL, apiKey, apiSecret, idCompatMode);
    }

    public Mono<List<ApiKeySpec>> listClusterApiKeys(String clusterId) {
        log.debug("List Cluster API Keys");
        return doPaginatedGet("/iam/v2/api-keys?spec.resource=" + clusterId, this::readApiKey,
                "Could not retrieve cluster API Key list");
    }

    public Mono<List<ServiceAccountSpec>> listServiceAccounts() {
        log.debug("List service accounts");
        return doPaginatedGet("/iam/v2/service-accounts", obj -> toServiceAccountSpec(obj),
                "Could not retrieve service accounts");
    }

    public Mono<ServiceAccountSpec> createServiceAccount(String accountName, String accountDescription) {
        log.debug("Create Service Account {}", accountName);
        JSONObject req = new JSONObject();
        req.put("display_name", accountName);
        req.put("description", accountDescription);

        return doPost("/iam/v2/service-accounts", req.toString(), response -> toServiceAccountSpec(response),
                "Could not create service account").flatMap(spec -> perhapsAddInternalId(spec));
    }

    public Mono<ApiKeySpec> createApiKey(String envId, String clusterId, String description,
            String serviceAccountResourceId) {
        log.debug("Create API Key {}", description);
        JSONObject spec = new JSONObject();

        spec.put("display_name", "");
        spec.put("description", description);
        spec.put("owner", Map.of("id", serviceAccountResourceId, "environment", envId));
        spec.put("resource", Map.of("id", clusterId, "environment", envId));
        String request = new JSONObject(Map.of("spec", spec)).toString();

        return doPost("/iam/v2/api-keys", request, this::readApiKey, "Could not create API Key");
    }

    public Mono<Boolean> deleteApiKey(ApiKeySpec apiKeySpec) {
        log.debug("Delete API Key {}", apiKeySpec.getId());
        return doDelete("/iam/v2/api-keys/" + apiKeySpec.getId(), "Could not delete API key").map(o -> true);
    }

    /**
     * Uses the unofficial endpoint https://api.confluent.cloud/service_accounts to retrieve the internal numeric IDs
     * for each Service Account in the current organization.
     *
     * @return A map from Confluent resource IDs (e.g. sa-xy123) to numeric service account IDs (e.g. 123456).
     */
    public Mono<Map<String, String>> getServiceAccountInternalIds() {
        log.debug("Get service account numeric IDs");
        return doDirectGet("/service_accounts", "Could not access or read /service_accounts workaround endpoint")
                .flatMap(response -> toServiceAccountIdMap(response));
    }

    private ApiKeySpec readApiKey(JSONObject obj) {
        JSONObject spec = obj.getJSONObject("spec");
        JSONObject metadata = obj.getJSONObject("metadata");
        ApiKeySpec result = new ApiKeySpec();
        result.setId(obj.getString("id"));
        result.setDescription(spec.getString("description"));
        result.setCreatedAt(Instant.parse(metadata.getString("created_at")));
        result.setSecret(
                spec.has("secret") && StringUtils.hasLength(spec.getString("secret")) ? spec.getString("secret")
                        : null);
        result.setServiceAccountId(spec.getJSONObject("owner").getString("id"));
        return result;
    }

    private Mono<Map<String, String>> toServiceAccountIdMap(String jsonResponse) {
        try {
            JSONObject obj = new JSONObject(jsonResponse);
            JSONArray users = obj.getJSONArray("users");
            Map<String, String> result = new HashMap<>();
            for (int i = 0; i < users.length(); i++) {
                JSONObject user = users.getJSONObject(i);
                if (user.optBoolean("service_account")) {
                    result.put(user.getString("resource_id"), user.get("id").toString());
                }
            }
            return Mono.just(result);
        }
        catch (JSONException e) {
            return Mono.error(e);
        }
    }

    private Mono<ServiceAccountSpec> perhapsAddInternalId(ServiceAccountSpec spec) {
        if (idCompatMode) {
            return getServiceAccountInternalIds().map(idMap -> {
                spec.setNumericId(idMap.get(spec.getResourceId()));
                return spec;
            });
        }
        return Mono.just(spec);
    }

    private <T> Mono<T> doPost(String uri, String body, SingleObjectMapper<T> responseBodyHandler,
            String errorMessage) {
        log.debug("Confluent API POST Request: uri = {}, body = {}", uri, body);
        return doMethod(WebClient::post, uri, body, responseBodyHandler, errorMessage);
    }

    @SuppressWarnings("SameParameterValue")
    private Mono<String> doDirectGet(String uri, String errorMessage) {
        log.debug("Confluent API GET Request: uri = {}", uri);

        return client.get().uri(uri).retrieve()
                .onStatus(status -> status.isError(), errorResponseHandler(uri, errorMessage)).bodyToMono(String.class);
    }

    private <T> Mono<List<T>> doPaginatedGet(String uri, SingleObjectMapper<T> dataElementExtractor,
            String errorMessage) {
        log.debug("Confluent API GET Request (using pagination): uri = {}", uri);

        String localUri = uri;

        if (!uri.contains("page_token")) {
            if (uri.contains("?")) {
                localUri = uri + "&page_size=100";
            }
            else {
                localUri = uri + "?page_size=100";
            }
        }

        // We perform our own parsing, as otherwise, Spring would expand %3D in the String (returned by "next" page of
        // Confluent Cloud API) to %253D.
        URI realUri;
        try {
            realUri = new URI(baseUrl).resolve(new URI(localUri));
        }
        catch (URISyntaxException e) {
            log.error("Could not perform REST API request due to invalid URI {}", localUri, e);
            return Mono.just(List.of());
        }

        return client.get().uri(realUri).retrieve()
                .onStatus(status -> status.isError(), errorResponseHandler(uri, errorMessage)).bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        JSONObject objBody = new JSONObject(body);
                        if (!objBody.has("data")) {
                            return Mono.error(new ConfluentApiException(
                                    "Paginated endpoint " + uri + " did not return a data array"));
                        }
                        if (!objBody.has("metadata")) {
                            return Mono.error(new ConfluentApiException(
                                    "Paginated endpoint " + uri + " did not return pagination metadata"));
                        }

                        JSONObject metadata = objBody.getJSONObject("metadata");

                        JSONArray data = objBody.getJSONArray("data");
                        List<T> values = new ArrayList<>();
                        for (int i = 0; i < data.length(); i++) {
                            try {
                                values.add(dataElementExtractor.apply(data.getJSONObject(i)));
                            }
                            catch (JSONException | JsonProcessingException e) {
                                return Mono
                                        .error(new ConfluentApiException("Could not parse element of data array", e));
                            }
                        }

                        if (metadata.has("next")) {
                            String relativeUri = metadata.getString("next").replace(BASE_URL, "");
                            return doPaginatedGet(relativeUri, dataElementExtractor, errorMessage).map(
                                    ls -> Stream.concat(values.stream(), ls.stream()).collect(Collectors.toList()));
                        }
                        return Mono.just(values);
                    }
                    catch (JSONException e) {
                        return Mono.error(new ConfluentApiException("Could not parse Confluent Cloud API response", e));
                    }
                });
    }

    @SuppressWarnings("SameParameterValue")
    private Mono<?> doDelete(String uri, String errorMessage) {
        return client.delete().uri(uri).retrieve()
                .onStatus(status -> status.isError(), errorResponseHandler(uri, errorMessage)).toBodilessEntity();
    }

    private <T> Mono<T> doMethod(Function<WebClient, WebClient.RequestBodyUriSpec> method, String uri, String body,
            SingleObjectMapper<T> responseBodyHandler, String errorMessage) {
        return method.apply(client).uri(uri).body(BodyInserters.fromValue(body)).retrieve()
                .onStatus(status -> status.isError(), errorResponseHandler(uri, errorMessage)).bodyToMono(String.class)
                .map(response -> {
                    try {
                        return responseBodyHandler.apply(new JSONObject(response));
                    }
                    catch (JSONException | JsonProcessingException e) {
                        throw new ConfluentApiException("Could not parse Confluent Cloud API response", e);
                    }
                });
    }

    private ServiceAccountSpec toServiceAccountSpec(JSONObject jsonResponse) throws JSONException {
        ServiceAccountSpec spec = new ServiceAccountSpec();
        spec.setDisplayName(jsonResponse.getString("display_name"));
        spec.setResourceId(jsonResponse.getString("id"));
        spec.setDescription(jsonResponse.getString("description"));
        return spec;
    }

    private Function<ClientResponse, Mono<? extends Throwable>> errorResponseHandler(String uri, String errorMessage) {
        return response -> response.bodyToMono(String.class).map(body -> {
            try {
                JSONObject result = new JSONObject(body);
                if (result.has("error") && !result.isNull("error")) {
                    return new ConfluentApiException(errorMessage + ": " + result.getString("error"));
                }
                if (result.has("errors") && result.getJSONArray("errors").length() > 0) {
                    return createApiExceptionFromErrors(errorMessage, result.getJSONArray("errors"));
                }
            }
            catch (JSONException e) {
                // then fallback to simple exception
            }
            return new ConfluentApiException(
                    errorMessage + ": Server returned " + response.statusCode().value() + " for " + uri);

        }).defaultIfEmpty(new ConfluentApiException(
                errorMessage + ": Server returned " + response.statusCode().value() + " for " + uri));
    }

    // currently, we only take the first error of the array, and use its "detail" as error message.
    // Maybe in future, we can add more details to the Exception object, if required.
    private ConfluentApiException createApiExceptionFromErrors(String errorMessage, JSONArray errors) {
        JSONObject error = errors.getJSONObject(0);
        String msg = error.getString("detail");
        return new ConfluentApiException(errorMessage + ": " + msg);
    }

    private static String buildAuth(String apiKey, String apiSecret) {
        return "Basic " + HttpHeaders.encodeBasicAuth(apiKey, apiSecret, StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface SingleObjectMapper<T> {
        T apply(JSONObject obj) throws JsonProcessingException;
    }

}
