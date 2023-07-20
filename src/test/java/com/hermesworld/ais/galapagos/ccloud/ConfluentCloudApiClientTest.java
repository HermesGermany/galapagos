package com.hermesworld.ais.galapagos.ccloud;

import com.hermesworld.ais.galapagos.ccloud.apiclient.ApiKeySpec;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ConfluentApiException;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ConfluentCloudApiClient;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ServiceAccountSpec;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ConfluentCloudApiClientTest {

    private static MockWebServer mockBackEnd;

    private String baseUrl;

    @BeforeAll
    static void setUp() throws IOException {
        mockBackEnd = new MockWebServer();
        mockBackEnd.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockBackEnd.shutdown();
    }

    @BeforeEach
    void init() {
        baseUrl = "http://localhost:%s".formatted(mockBackEnd.getPort());
    }

    @AfterEach
    void consumeRequests() throws Exception {
        RecordedRequest request;
        while ((request = mockBackEnd.takeRequest(10, TimeUnit.MILLISECONDS)) != null) {
            System.err.println(
                    "WARN: Unconsumed request found in mockBackEnd queue. Every test case should consume all expected requests.");
            System.err.println("Request was " + request.getMethod() + " " + request.getRequestUrl());
        }
    }

    private String readTestResource(String resourceName) throws IOException {
        try (InputStream in = ConfluentCloudApiClientTest.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new NoSuchElementException("Could not find test resource: " + resourceName);
            }
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }

    @Test
    void testListServiceAccounts() throws Exception {
        mockBackEnd.enqueue(new MockResponse().setBody(readTestResource("ccloud/service-accounts.json"))
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);

        List<ServiceAccountSpec> accounts = apiClient.listServiceAccounts().block();
        assertNotNull(accounts);
        assertEquals(2, accounts.size());
        assertEquals("service-account-one", accounts.get(0).getDisplayName());
        assertEquals("sa-xy123", accounts.get(0).getResourceId());
        assertEquals("service-account-two", accounts.get(1).getDisplayName());
        assertEquals("sa-xy124", accounts.get(1).getResourceId());

        RecordedRequest recordedRequest = mockBackEnd.takeRequest(10, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("/iam/v2/service-accounts", Objects.requireNonNull(recordedRequest.getRequestUrl()).encodedPath());
        assertEquals("Basic " + HttpHeaders.encodeBasicAuth("myKey", "mySecret", StandardCharsets.UTF_8),
                recordedRequest.getHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void testPagination() throws Exception {
        mockBackEnd.enqueue(new MockResponse()
                .setBody(readTestResource("ccloud/service-accounts-page1.json").replace("${baseurl}", baseUrl))
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
        mockBackEnd.enqueue(new MockResponse().setBody(readTestResource("ccloud/service-accounts-page2.json"))
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

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

        RecordedRequest recordedRequest = mockBackEnd.takeRequest(10, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("/iam/v2/service-accounts", Objects.requireNonNull(recordedRequest.getRequestUrl()).encodedPath());
        recordedRequest = mockBackEnd.takeRequest(10, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        // second request must still be authenticated
        assertEquals("Basic " + HttpHeaders.encodeBasicAuth("myKey", "mySecret", StandardCharsets.UTF_8),
                recordedRequest.getHeader(HttpHeaders.AUTHORIZATION));
        assertEquals("/next_page", Objects.requireNonNull(recordedRequest.getRequestUrl()).encodedPath());
        assertEquals("page_token=ABC", Objects.requireNonNull(recordedRequest.getRequestUrl()).encodedQuery());
    }

    @Test
    void testListApiKeys() throws Exception {
        mockBackEnd.enqueue(new MockResponse().setBody(readTestResource("ccloud/api-keys.json"))
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);

        List<ApiKeySpec> apiKeys = apiClient.listClusterApiKeys("lkc-mycluster").block();
        assertNotNull(apiKeys);

        assertEquals(1, apiKeys.size());
        assertEquals("ABCDEFG123456", apiKeys.get(0).getId());
        assertEquals("My API Key", apiKeys.get(0).getDescription());
        assertEquals("sa-xy123", apiKeys.get(0).getServiceAccountId());
        assertEquals("2022-09-16T11:45:01.722675Z", apiKeys.get(0).getCreatedAt().toString());

        RecordedRequest recordedRequest = mockBackEnd.takeRequest(10, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("/iam/v2/api-keys", Objects.requireNonNull(recordedRequest.getRequestUrl()).encodedPath());

        assertEquals("lkc-mycluster",
                Objects.requireNonNull(recordedRequest.getRequestUrl()).queryParameter("spec.resource"));
    }

    @Test
    void testCreateServiceAccount() throws Exception {
        mockBackEnd.enqueue(new MockResponse().setBody(readTestResource("ccloud/service-account.json"))
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setResponseCode(HttpStatus.CREATED.value()));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);

        ServiceAccountSpec spec = apiClient.createServiceAccount("myaccount", "mydesc").block();
        assertNotNull(spec);

        RecordedRequest recordedRequest = mockBackEnd.takeRequest(10, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals(HttpMethod.POST.name(), recordedRequest.getMethod());
        assertEquals("/iam/v2/service-accounts", Objects.requireNonNull(recordedRequest.getRequestUrl()).encodedPath());
        assertEquals("Created Service Account.", spec.getDescription());
        assertEquals("CREATED_service_account", spec.getDisplayName());
        assertEquals("sa-xy123", spec.getResourceId());
        assertNull(spec.getNumericId());

        String requestBody = recordedRequest.getBody().readUtf8();
        JSONObject requestObj = new JSONObject(requestBody);
        assertEquals("myaccount", requestObj.getString("display_name"));
        assertEquals("mydesc", requestObj.getString("description"));
    }

    @Test
    void testCreateServiceAccount_withNumericId() throws Exception {
        mockBackEnd.enqueue(new MockResponse().setBody(readTestResource("ccloud/service-account.json"))
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setResponseCode(HttpStatus.CREATED.value()));
        mockBackEnd.enqueue(new MockResponse().setBody(readTestResource("ccloud/service-account-mapping.json"))
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", true);

        ServiceAccountSpec spec = apiClient.createServiceAccount("myaccount", "mydesc").block();
        assertNotNull(spec);

        RecordedRequest createRequest = mockBackEnd.takeRequest(10, TimeUnit.SECONDS);
        RecordedRequest mappingRequest = mockBackEnd.takeRequest(10, TimeUnit.SECONDS);

        assertNotNull(createRequest);
        assertNotNull(mappingRequest);

        assertEquals("/iam/v2/service-accounts", Objects.requireNonNull(createRequest.getRequestUrl()).encodedPath());
        assertEquals("/service_accounts", Objects.requireNonNull(mappingRequest.getRequestUrl()).encodedPath());

        assertEquals("123456", spec.getNumericId());
    }

    @Test
    void testCreateApiKey() throws Exception {
        mockBackEnd.enqueue(new MockResponse().setBody(readTestResource("ccloud/api-key.json"))
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setResponseCode(HttpStatus.ACCEPTED.value()));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);

        ApiKeySpec spec = apiClient.createApiKey("env-ab123", "lkc-abc123", "description param", "sa-xy123").block();
        assertNotNull(spec);

        RecordedRequest recordedRequest = mockBackEnd.takeRequest(10, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals(HttpMethod.POST.name(), recordedRequest.getMethod());
        assertEquals("/iam/v2/api-keys", Objects.requireNonNull(recordedRequest.getRequestUrl()).encodedPath());

        assertEquals("ABCDEF123456", spec.getId());
        assertEquals("2022-07-22T14:48:41.966079Z", spec.getCreatedAt().toString());
        assertEquals("API Key Description", spec.getDescription());
        assertEquals("sa-xy123", spec.getServiceAccountId());

        String requestBody = recordedRequest.getBody().readUtf8();
        JSONObject requestObj = new JSONObject(requestBody);

        JSONObject specObj = requestObj.getJSONObject("spec");
        assertEquals("", specObj.getString("display_name"));
        assertEquals("description param", specObj.getString("description"));

        JSONObject ownerObj = specObj.getJSONObject("owner");
        assertEquals("sa-xy123", ownerObj.getString("id"));
        assertEquals("env-ab123", ownerObj.getString("environment"));

        JSONObject resObj = specObj.getJSONObject("resource");
        assertEquals("lkc-abc123", resObj.getString("id"));
        assertEquals("env-ab123", resObj.getString("environment"));
    }

    @Test
    void testDeleteApiKey() throws Exception {
        mockBackEnd.enqueue(new MockResponse().setResponseCode(HttpStatus.NO_CONTENT.value()));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);

        ApiKeySpec spec = new ApiKeySpec();
        spec.setId("ABCDEF123456");

        apiClient.deleteApiKey(spec).block();

        RecordedRequest recordedRequest = mockBackEnd.takeRequest(10, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals(HttpMethod.DELETE.name(), recordedRequest.getMethod());
        assertEquals("/iam/v2/api-keys/ABCDEF123456",
                Objects.requireNonNull(recordedRequest.getRequestUrl()).encodedPath());
    }

    @Test
    void testErrorStatusCode() throws Exception {
        mockBackEnd.enqueue(new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value()));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);
        ApiKeySpec spec = new ApiKeySpec();
        spec.setId("ABCDEF123456");

        StepVerifier.create(apiClient.deleteApiKey(spec)).expectErrorMatches(t -> (t instanceof ConfluentApiException)
                && t.getMessage().startsWith("Could not delete API key: Server returned 404 for ")).verify();

        assertNotNull(mockBackEnd.takeRequest(10, TimeUnit.SECONDS));
    }

    @Test
    void testErrorMessage_singleError() throws Exception {
        JSONObject errorObj = new JSONObject(Map.of("error", "something went wrong"));
        mockBackEnd.enqueue(new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value())
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).setBody(errorObj.toString()));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);
        ApiKeySpec spec = new ApiKeySpec();
        spec.setId("ABCDEF123456");

        StepVerifier.create(apiClient.deleteApiKey(spec)).expectErrorMatches(t -> (t instanceof ConfluentApiException)
                && t.getMessage().equals("Could not delete API key: something went wrong")).verify();

        assertNotNull(mockBackEnd.takeRequest(10, TimeUnit.SECONDS));
    }

    @Test
    void testErrorMessage_errorsArray() throws Exception {
        JSONObject errorObj = new JSONObject(Map.of("detail", "something went wrong"));
        JSONObject errorObj2 = new JSONObject(Map.of("detail", "all is broken"));
        JSONArray errors = new JSONArray();
        errors.put(errorObj);
        errors.put(errorObj2);
        JSONObject body = new JSONObject(Map.of("errors", errors));

        mockBackEnd.enqueue(new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value())
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).setBody(body.toString()));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);
        ApiKeySpec spec = new ApiKeySpec();
        spec.setId("ABCDEF123456");

        StepVerifier.create(apiClient.deleteApiKey(spec)).expectErrorMatches(t -> (t instanceof ConfluentApiException)
                && t.getMessage().equals("Could not delete API key: something went wrong")).verify();

        assertNotNull(mockBackEnd.takeRequest(10, TimeUnit.SECONDS));
    }

    @Test
    void testError_textOnlyResponse() throws Exception {
        mockBackEnd.enqueue(new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value())
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .setBody("This is your friendly error message in text only."));

        ConfluentCloudApiClient apiClient = new ConfluentCloudApiClient(baseUrl, "myKey", "mySecret", false);
        ApiKeySpec spec = new ApiKeySpec();
        spec.setId("ABCDEF123456");

        StepVerifier.create(apiClient.deleteApiKey(spec)).expectErrorMatches(t -> (t instanceof ConfluentApiException)
                && t.getMessage().startsWith("Could not delete API key: Server returned 400 for ")).verify();

        assertNotNull(mockBackEnd.takeRequest(10, TimeUnit.SECONDS));
    }

}
