package com.hermesworld.ais.galapagos.ccloud;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.*;
import com.github.tomakehurst.wiremock.verification.NearMiss;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ApiKeySpec;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ConfluentApiException;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ConfluentCloudApiClient;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ServiceAccountSpec;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@WireMockTest
class ConfluentCloudApiClientTest {

    private static final String SERVICE_ACCOUNTS_ENDPOINT = "/iam/v2/service-accounts";

    private static final String API_KEYS_ENDPOINT = "/iam/v2/api-keys";

    private String baseUrl;

    private WireMock wireMock;

    @BeforeEach
    void init(WireMockRuntimeInfo info) {
        wireMock = info.getWireMock();
        baseUrl = "http://localhost:%s".formatted(info.getHttpPort());
    }

    @AfterEach
    void checkWireMockStatus(WireMockRuntimeInfo info) {
        WireMock wireMock = info.getWireMock();
        wireMock.findAllUnmatchedRequests().forEach(req -> {
            System.err.println("Unmatched request: " + req.getAbsoluteUrl());
            List<NearMiss> nearMisses = WireMock.findNearMissesFor(req);
            nearMisses.forEach(miss -> System.err.println("Potential near miss:" + miss.getDiff()));
        });
    }

    private String readTestResource(String resourceName) throws IOException {
        try (InputStream in = ConfluentCloudApiClientTest.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new NoSuchElementException("Could not find test resource: " + resourceName);
            }
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }

    private static MappingBuilder authenticatedEndpoint(String path, HttpMethod method) {
        UrlPathPattern urlPattern = urlPathEqualTo(path);
        MappingBuilder builder = switch (method.name()) {
        case "POST" -> post(urlPattern);
        case "PUT" -> put(urlPattern);
        case "DELETE" -> delete(urlPattern);
        case "PATCH" -> patch(urlPattern);
        default -> get(urlPattern);
        };

        return builder.withBasicAuth("myKey", "mySecret");
    }

    private static MappingBuilder authenticatedEndpoint(String path) {
        return authenticatedEndpoint(path, HttpMethod.GET);
    }

    private static MappingBuilder serviceAccountsEndpoint(HttpMethod method) {
        return authenticatedEndpoint(SERVICE_ACCOUNTS_ENDPOINT, method);
    }

    private static MappingBuilder serviceAccountsEndpoint() {
        return serviceAccountsEndpoint(HttpMethod.GET).withQueryParam("page_size", new RegexPattern("[0-9]+"));
    }

    private static ResponseDefinitionBuilder okForPlainJson(String jsonSource) {
        return ResponseDefinitionBuilder.responseDefinition().withStatus(HttpStatus.OK.value()).withBody(jsonSource)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void testListServiceAccounts() throws Exception {
        wireMock.register(
                serviceAccountsEndpoint().willReturn(okForPlainJson(readTestResource("ccloud/service-accounts.json"))));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);

        List<ServiceAccountSpec> accounts = apiClient.listServiceAccounts().block();
        assertNotNull(accounts);
        assertEquals(2, accounts.size());
        assertEquals("service-account-one", accounts.get(0).getDisplayName());
        assertEquals("sa-xy123", accounts.get(0).getResourceId());
        assertEquals("service-account-two", accounts.get(1).getDisplayName());
        assertEquals("sa-xy124", accounts.get(1).getResourceId());

        wireMock.verifyThat(1, requestedFor(HttpMethod.GET.name(), urlPathEqualTo(SERVICE_ACCOUNTS_ENDPOINT)));
    }

    @Test
    void testPagination() throws Exception {
        wireMock.register(serviceAccountsEndpoint().willReturn(
                okForPlainJson(readTestResource("ccloud/service-accounts-page1.json").replace("${baseurl}", baseUrl))));
        wireMock.register(get(urlPathEqualTo("/next_page")).withBasicAuth("myKey", "mySecret")
                .withQueryParam("page_token", new EqualToPattern("ABC"))
                .willReturn(okForPlainJson(readTestResource("ccloud/service-accounts-page2.json"))));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);

        Consumer<List<ServiceAccountSpec>> verifyAccounts = accounts -> {
            assertEquals(3, accounts.size());

            assertEquals("service-account-one", accounts.get(0).getDisplayName());
            assertEquals("sa-xy123", accounts.get(0).getResourceId());
            assertEquals("service-account-two", accounts.get(1).getDisplayName());
            assertEquals("sa-xy124", accounts.get(1).getResourceId());
            assertEquals("service-account-three", accounts.get(2).getDisplayName());
            assertEquals("sa-xy125", accounts.get(2).getResourceId());
        };

        StepVerifier.create(apiClient.listServiceAccounts()).assertNext(verifyAccounts).verifyComplete();

        wireMock.verifyThat(1, requestedFor(HttpMethod.GET.name(), urlPathEqualTo(SERVICE_ACCOUNTS_ENDPOINT)));
        wireMock.verifyThat(1, requestedFor(HttpMethod.GET.name(), urlPathEqualTo("/next_page")));
    }

    @Test
    void testListApiKeys() throws Exception {
        wireMock.register(authenticatedEndpoint(API_KEYS_ENDPOINT)
                .withQueryParam("spec.resource", new EqualToPattern("lkc-mycluster"))
                .willReturn(okForPlainJson(readTestResource("ccloud/api-keys.json"))));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);

        List<ApiKeySpec> apiKeys = apiClient.listClusterApiKeys("lkc-mycluster").block();
        assertNotNull(apiKeys);

        assertEquals(1, apiKeys.size());
        assertEquals("ABCDEFG123456", apiKeys.get(0).getId());
        assertEquals("My API Key", apiKeys.get(0).getDescription());
        assertEquals("sa-xy123", apiKeys.get(0).getServiceAccountId());
        assertEquals("2022-09-16T11:45:01.722675Z", apiKeys.get(0).getCreatedAt().toString());

        wireMock.verifyThat(1, requestedFor(HttpMethod.GET.name(), urlPathEqualTo(API_KEYS_ENDPOINT)));
    }

    @Test
    void testCreateServiceAccount() throws Exception {
        wireMock.register(serviceAccountsEndpoint(HttpMethod.POST)
                .withRequestBody(
                        new JsonWithPropertiesPattern(Map.of("display_name", "myaccount", "description", "mydesc")))
                .willReturn(okForPlainJson(readTestResource("ccloud/service-account.json"))
                        .withStatus(HttpStatus.CREATED.value())));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);

        ServiceAccountSpec spec = apiClient.createServiceAccount("myaccount", "mydesc").block();
        assertNotNull(spec);

        assertEquals("Created Service Account.", spec.getDescription());
        assertEquals("CREATED_service_account", spec.getDisplayName());
        assertEquals("sa-xy123", spec.getResourceId());
        assertNull(spec.getNumericId());

        wireMock.verifyThat(1, requestedFor(HttpMethod.POST.name(), urlPathEqualTo(SERVICE_ACCOUNTS_ENDPOINT)));
    }

    @Test
    void testCreateServiceAccount_withNumericId() throws Exception {
        wireMock.register(serviceAccountsEndpoint(HttpMethod.POST)
                .willReturn(okForPlainJson(readTestResource("ccloud/service-account.json"))
                        .withStatus(HttpStatus.CREATED.value())));
        wireMock.register(authenticatedEndpoint("/service_accounts")
                .willReturn(okForPlainJson(readTestResource("ccloud/service-account-mapping.json"))));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", true);

        ServiceAccountSpec spec = apiClient.createServiceAccount("myaccount", "mydesc").block();
        assertNotNull(spec);
        assertEquals("123456", spec.getNumericId());

        wireMock.verifyThat(1, requestedFor(HttpMethod.POST.name(), urlPathEqualTo(SERVICE_ACCOUNTS_ENDPOINT)));
        wireMock.verifyThat(1, requestedFor(HttpMethod.GET.name(), urlPathEqualTo("/service_accounts")));
    }

    @Test
    void testCreateApiKey() throws Exception {
        Map<String, String> expectedJsonProperties = Map.of("spec.display_name", "", "spec.description",
                "description param", "spec.owner.id", "sa-xy123", "spec.owner.environment", "env-ab123",
                "spec.resource.id", "lkc-abc123", "spec.resource.environment", "env-ab123");

        wireMock.register(authenticatedEndpoint(API_KEYS_ENDPOINT, HttpMethod.POST)
                .withRequestBody(new JsonWithPropertiesPattern(expectedJsonProperties))
                .willReturn(okForPlainJson(readTestResource("ccloud/api-key.json"))
                        .withStatus(HttpStatus.ACCEPTED.value())));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);

        ApiKeySpec spec = apiClient.createApiKey("env-ab123", "lkc-abc123", "description param", "sa-xy123").block();
        assertNotNull(spec);

        assertEquals("ABCDEF123456", spec.getId());
        assertEquals("2022-07-22T14:48:41.966079Z", spec.getCreatedAt().toString());
        assertEquals("API Key Description", spec.getDescription());
        assertEquals("sa-xy123", spec.getServiceAccountId());

        wireMock.verifyThat(1, requestedFor(HttpMethod.POST.name(), urlPathEqualTo(API_KEYS_ENDPOINT)));
    }

    @Test
    void testDeleteApiKey() {
        wireMock.register(
                authenticatedEndpoint(API_KEYS_ENDPOINT + "/ABCDEF123456", HttpMethod.DELETE).willReturn(noContent()));
        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);

        ApiKeySpec spec = new ApiKeySpec();
        spec.setId("ABCDEF123456");

        apiClient.deleteApiKey(spec).block();
        wireMock.verifyThat(1,
                requestedFor(HttpMethod.DELETE.name(), urlPathEqualTo(API_KEYS_ENDPOINT + "/ABCDEF123456")));
    }

    @Test
    void testErrorStatusCode() {
        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);
        ApiKeySpec spec = new ApiKeySpec();
        spec.setId("ABCDEF123456");

        StepVerifier.create(apiClient.deleteApiKey(spec)).expectErrorMatches(t -> (t instanceof ConfluentApiException)
                && t.getMessage().startsWith("Could not delete API key: Server returned 404 for ")).verify();
        wireMock.resetRequests();
    }

    @Test
    void testErrorMessage_singleError() {
        JSONObject errorObj = new JSONObject(Map.of("error", "something went wrong"));

        wireMock.register(authenticatedEndpoint(API_KEYS_ENDPOINT + "/ABCDEF123456", HttpMethod.DELETE)
                .willReturn(badRequest().withBody(errorObj.toString()).withHeader(HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE)));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);
        ApiKeySpec spec = new ApiKeySpec();
        spec.setId("ABCDEF123456");

        StepVerifier.create(apiClient.deleteApiKey(spec)).expectErrorMatches(t -> (t instanceof ConfluentApiException)
                && t.getMessage().equals("Could not delete API key: something went wrong")).verify();
    }

    @Test
    void testErrorMessage_errorsArray() {
        JSONObject errorObj = new JSONObject(Map.of("detail", "something went wrong"));
        JSONObject errorObj2 = new JSONObject(Map.of("detail", "all is broken"));
        JSONArray errors = new JSONArray();
        errors.put(errorObj);
        errors.put(errorObj2);
        JSONObject body = new JSONObject(Map.of("errors", errors));

        wireMock.register(authenticatedEndpoint(API_KEYS_ENDPOINT + "/ABCDEF123456", HttpMethod.DELETE)
                .willReturn(badRequest().withBody(body.toString()).withHeader(HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE)));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);
        ApiKeySpec spec = new ApiKeySpec();
        spec.setId("ABCDEF123456");

        StepVerifier.create(apiClient.deleteApiKey(spec)).expectErrorMatches(t -> (t instanceof ConfluentApiException)
                && t.getMessage().equals("Could not delete API key: something went wrong")).verify();
    }

    @Test
    void testError_textOnlyResponse() {
        wireMock.register(authenticatedEndpoint(API_KEYS_ENDPOINT + "/ABCDEF123456", HttpMethod.DELETE)
                .willReturn(badRequest().withBody("This is your friendly error message in text only.")
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);
        ApiKeySpec spec = new ApiKeySpec();
        spec.setId("ABCDEF123456");

        StepVerifier.create(apiClient.deleteApiKey(spec)).expectErrorMatches(t -> (t instanceof ConfluentApiException)
                && t.getMessage().startsWith("Could not delete API key: Server returned 400 for ")).verify();
    }

    private static class JsonWithPropertiesPattern extends ContentPattern<byte[]> {

        private final Map<String, String> propertiesAndValues;

        public JsonWithPropertiesPattern(Map<String, String> propertiesAndValues) {
            super(propertiesAndValues.toString().getBytes(StandardCharsets.UTF_8));
            this.propertiesAndValues = propertiesAndValues;
        }

        @Override
        public String getName() {
            return "json-with-properties";
        }

        @Override
        public String getExpected() {
            return propertiesAndValues.toString();
        }

        private boolean containsProperty(JSONObject obj, String propertyName, String expectedValue) {
            if (propertyName.contains(".")) {
                String pn = propertyName.split("\\.")[0];
                if (!obj.has(pn)) {
                    return false;
                }
                return containsProperty(obj.getJSONObject(pn), propertyName.substring(propertyName.indexOf('.') + 1),
                        expectedValue);
            }
            return obj.has(propertyName) && Objects.equals(obj.getString(propertyName), expectedValue);
        }

        @Override
        public MatchResult match(byte[] value) {
            try {
                JSONObject obj = new JSONObject(new String(value, StandardCharsets.UTF_8));

                int matchCounter = 0;
                for (Map.Entry<String, String> pairs : propertiesAndValues.entrySet()) {
                    if (containsProperty(obj, pairs.getKey(), pairs.getValue())) {
                        matchCounter++;
                    }
                }

                if (matchCounter == propertiesAndValues.size()) {
                    return MatchResult.exactMatch();
                }
                if (matchCounter == 0) {
                    return MatchResult.noMatch();
                }

                return MatchResult.partialMatch(propertiesAndValues.size() - matchCounter);
            }
            catch (JSONException e) {
                return MatchResult.noMatch();
            }
        }
    }

}
