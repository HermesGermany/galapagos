package com.hermesworld.ais.galapagos.ccloud;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ApiKeySpec;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ConfluentCloudApiClient;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ServiceAccountSpec;
import com.hermesworld.ais.galapagos.ccloud.auth.ConfluentCloudAuthConfig;
import com.hermesworld.ais.galapagos.ccloud.auth.ConfluentCloudAuthUtil;
import com.hermesworld.ais.galapagos.ccloud.auth.ConfluentCloudAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.auth.CreateAuthenticationResult;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import org.hamcrest.core.StringContains;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

class ConfluentCloudAuthenticationModuleTest {

    private KafkaAuthenticationModule authenticationModule;

    private ConfluentCloudApiClient client;

    @BeforeEach
    void init() {
        authenticationModule = new ConfluentCloudAuthenticationModule(basicConfig());
        client = mock(ConfluentCloudApiClient.class);

        ReflectionTestUtils.setField(authenticationModule, "client", client);
    }

    private ConfluentCloudAuthConfig basicConfig() {
        ConfluentCloudAuthConfig config = new ConfluentCloudAuthConfig();
        config.setEnvironmentId("testEnv");
        config.setClusterId("testCluster");
        config.setOrganizationApiKey("orgApiKey");
        config.setOrganizationApiSecret("orgApiTopSecret123");
        config.setServiceAccountIdCompatMode(false);
        return config;
    }

    private void useIdCompatMode() {
        ConfluentCloudAuthConfig config = basicConfig();
        config.setServiceAccountIdCompatMode(true);
        authenticationModule = new ConfluentCloudAuthenticationModule(config);
        ReflectionTestUtils.setField(authenticationModule, "client", client);
    }

    private void enableDeveloperAuthentication() {
        ConfluentCloudAuthConfig config = basicConfig();
        config.setDeveloperApiKeyValidity("P7D");
        authenticationModule = new ConfluentCloudAuthenticationModule(config);
        ReflectionTestUtils.setField(authenticationModule, "client", client);
    }

    private ApiKeySpec newApiKey(String key, String secret, String serviceAccountResourceId) {
        ApiKeySpec spec = new ApiKeySpec();
        spec.setId(key);
        spec.setSecret(secret);
        spec.setCreatedAt(Instant.now());
        spec.setServiceAccountId(serviceAccountResourceId);
        return spec;
    }

    private String toAuthJson(Map<String, String> fields) {
        return new JSONObject(fields).toString();
    }

    @Test
    void extractKafkaUserNameTest_positive() {
        String kafkaUserName = authenticationModule.extractKafkaUserName(new JSONObject("{userId:1234}"));

        assertEquals("User:1234", kafkaUserName);
    }

    @Test
    void extractKafkaUserNameTest_negative() {
        assertThrows(JSONException.class, () -> authenticationModule.extractKafkaUserName(new JSONObject("{}")));
    }

    @Test
    void fillCorrectProps_positive() {
        ConfluentCloudAuthConfig config = basicConfig();
        config.setClusterApiSecret("secretPassword");
        config.setClusterApiKey("someApiKey");
        authenticationModule = new ConfluentCloudAuthenticationModule(config);

        Properties props = new Properties();
        authenticationModule.addRequiredKafkaProperties(props);

        assertEquals("PLAIN", props.getProperty("sasl.mechanism"));
        assertThat(props.getProperty("sasl.jaas.config"), StringContains.containsString("username='someApiKey'"));
        assertThat(props.getProperty("sasl.jaas.config"), StringContains.containsString("password='secretPassword'"));
    }

    @Test
    void fillCorrectProps_negative() {
        Properties props = new Properties();
        authenticationModule.addRequiredKafkaProperties(props);

        assertThat(props.getProperty("sasl.jaas.config"), StringContains.containsString("username='null'"));
        assertThat(props.getProperty("sasl.jaas.config"), StringContains.containsString("password='null'"));
    }

    @Test
    void testDeleteApplicationAuthentication_positive() throws ExecutionException, InterruptedException {
        ApiKeySpec apiKey1 = newApiKey("someKey1", "someSecret1", "sa-xy123");
        ApiKeySpec apiKey2 = newApiKey("someKey2", "someSecret2", "sa-xy124");

        when(client.listClusterApiKeys("testCluster")).thenReturn(Mono.just(List.of(apiKey1, apiKey2)));
        when(client.deleteApiKey(apiKey1)).thenReturn(Mono.just(true));

        authenticationModule.deleteApplicationAuthentication("quattro-1",
                new JSONObject(Map.of("userId", "sa-xy123", "apiKey", "someKey1"))).get();
        verify(client).deleteApiKey(apiKey1);
        verify(client, times(0)).createApiKey(any(), any(), any(), any());
    }

    @Test
    void deleteApplicationAuthenticationTest_negativeNoApiKeyObjectInAuthJson()
            throws ExecutionException, InterruptedException {
        ApiKeySpec apiKey1 = newApiKey("someKey1", "someSecret1", "sa-xy123");
        ApiKeySpec apiKey2 = newApiKey("someKey2", "someSecret2", "sa-xy124");

        when(client.listClusterApiKeys("testCluster")).thenReturn(Mono.just(List.of(apiKey1, apiKey2)));

        authenticationModule.deleteApplicationAuthentication("quattro-1", new JSONObject(Map.of("userId", "sa-xy123")))
                .get();
        verify(client, times(0)).deleteApiKey(any());
    }

    @Test
    void createApplicationAuthenticationTest_createServiceAccForAppThatHasNoAcc()
            throws ExecutionException, InterruptedException {
        ApiKeySpec apiKey1 = newApiKey("someKey1", "someSecret1", "sa-xy123");
        ApiKeySpec apiKey2 = newApiKey("someKey2", "someSecret2", "sa-xy124");
        ApiKeySpec apiKey3 = newApiKey("someKey3", "someSecret3", "sa-xy125");

        ServiceAccountSpec testServiceAccount = new ServiceAccountSpec();
        testServiceAccount.setResourceId("sa-xy125");
        testServiceAccount.setDisplayName("testName");

        when(client.listClusterApiKeys("testCluster")).thenReturn(Mono.just(List.of(apiKey1, apiKey2)));
        when(client.listServiceAccounts()).thenReturn(Mono.just(List.of()));
        when(client.createServiceAccount("application-normalizedAppNameTest", "APP_quattro-1"))
                .thenReturn(Mono.just(testServiceAccount));
        when(client.createApiKey("testEnv", "testCluster", "Application normalizedAppNameTest", "sa-xy125"))
                .thenReturn(Mono.just(apiKey3));

        CreateAuthenticationResult result = authenticationModule
                .createApplicationAuthentication("quattro-1", "normalizedAppNameTest", new JSONObject()).get();

        assertEquals("sa-xy125", result.getPublicAuthenticationData().getString("userId"));
        assertFalse(result.getPublicAuthenticationData().has("numericId"));
        assertEquals("someKey3", result.getPublicAuthenticationData().getString("apiKey"));

        verify(client, times(1)).createServiceAccount("application-normalizedAppNameTest", "APP_quattro-1");
        verify(client, times(1)).createApiKey("testEnv", "testCluster", "Application normalizedAppNameTest",
                "sa-xy125");
    }

    @Test
    void createApplicationAuthenticationTest_reuseServiceAccIfExists() throws ExecutionException, InterruptedException {
        ApiKeySpec apiKey1 = newApiKey("someKey1", "someSecret1", "sa-xy123");
        ApiKeySpec apiKey2 = newApiKey("someKey2", "someSecret2", "sa-xy124");
        ApiKeySpec apiKey3 = newApiKey("someKey3", "someSecret3", "sa-xy125");

        ServiceAccountSpec testServiceAccount = new ServiceAccountSpec();
        testServiceAccount.setResourceId("sa-xy125");
        testServiceAccount.setDisplayName("testName");
        testServiceAccount.setDescription("APP_quattro-1");

        when(client.listClusterApiKeys("testCluster")).thenReturn(Mono.just(List.of(apiKey1, apiKey2)));
        when(client.listServiceAccounts()).thenReturn(Mono.just(List.of(testServiceAccount)));
        when(client.createApiKey("testEnv", "testCluster", "Application normalizedAppNameTest", "sa-xy125"))
                .thenReturn(Mono.just(apiKey3));

        authenticationModule.createApplicationAuthentication("quattro-1", "normalizedAppNameTest", new JSONObject())
                .get();

        verify(client, times(0)).createServiceAccount(nullable(String.class), nullable(String.class));
        verify(client, times(1)).createApiKey("testEnv", "testCluster", "Application normalizedAppNameTest",
                "sa-xy125");
    }

    @Test
    void createApplicationAuthenticationTest_queryNumericId() throws ExecutionException, InterruptedException {
        useIdCompatMode();

        ApiKeySpec apiKey1 = newApiKey("someKey1", "someSecret1", "sa-xy123");

        ServiceAccountSpec testServiceAccount = new ServiceAccountSpec();
        testServiceAccount.setResourceId("sa-xy123");
        testServiceAccount.setDisplayName("testName");
        testServiceAccount.setDescription("APP_quattro-1");

        when(client.listClusterApiKeys("testCluster")).thenReturn(Mono.just(List.of()));
        when(client.listServiceAccounts()).thenReturn(Mono.just(List.of(testServiceAccount)));
        when(client.createApiKey("testEnv", "testCluster", "Application normalizedAppNameTest", "sa-xy123"))
                .thenReturn(Mono.just(apiKey1));
        Map<String, String> internalIdMapping = Map.of("sa-xy123", "12345", "sa-xy125", "12346");
        when(client.getServiceAccountInternalIds()).thenReturn(Mono.just(internalIdMapping));

        CreateAuthenticationResult result = authenticationModule
                .createApplicationAuthentication("quattro-1", "normalizedAppNameTest", new JSONObject()).get();

        assertEquals("sa-xy123", result.getPublicAuthenticationData().getString("userId"));
        assertEquals("12345", result.getPublicAuthenticationData().getString("numericId"));

        verify(client, times(0)).createServiceAccount(nullable(String.class), nullable(String.class));
        verify(client, times(1)).createApiKey("testEnv", "testCluster", "Application normalizedAppNameTest",
                "sa-xy123");
        verify(client, times(1)).getServiceAccountInternalIds();
    }

    @Test
    void updateApplicationAuthenticationTest() throws ExecutionException, InterruptedException {
        ApiKeySpec apiKey1 = newApiKey("someKey1", "someSecret1", "sa-xy123");
        ApiKeySpec apiKey2 = newApiKey("someKey2", "someSecret2", "sa-xy124");
        ApiKeySpec apiKey3 = newApiKey("someKey3", "someSecret3", "sa-xy123");

        ApplicationMetadata app = new ApplicationMetadata();
        app.setApplicationId("quattro-1");
        app.setAuthenticationJson(toAuthJson(Map.of("userId", "sa-xy123", "apiKey", "someKey1")));

        ServiceAccountSpec testServiceAccount = new ServiceAccountSpec();
        testServiceAccount.setResourceId("sa-xy123");
        testServiceAccount.setDisplayName("testName");
        testServiceAccount.setDescription("APP_quattro-1");

        when(client.listClusterApiKeys("testCluster")).thenReturn(Mono.just(List.of(apiKey1, apiKey2)));
        when(client.listServiceAccounts()).thenReturn(Mono.just(List.of(testServiceAccount)));
        when(client.deleteApiKey(apiKey1)).thenReturn(Mono.just(true));
        when(client.createApiKey("testEnv", "testCluster", "Application normalizedAppNameTest", "sa-xy123"))
                .thenReturn(Mono.just(apiKey3));

        String auth = app.getAuthenticationJson();

        authenticationModule.updateApplicationAuthentication("quattro-1", "normalizedAppNameTest", new JSONObject(),
                new JSONObject(auth)).get();

        verify(client).deleteApiKey(apiKey1);
        verify(client, times(0)).createServiceAccount(nullable(String.class), nullable(String.class));
        verify(client, times(1)).createApiKey("testEnv", "testCluster", "Application normalizedAppNameTest",
                "sa-xy123");
    }

    @Test
    void testLookupNumericId_positive() {
        useIdCompatMode();

        JSONObject authData = new JSONObject(Map.of("userId", "sa-xy125", "apiKey", "ABC123"));
        Map<String, String> internalIdMapping = Map.of("sa-xy123", "12399", "sa-xy125", "12345");

        when(client.getServiceAccountInternalIds()).thenReturn(Mono.just(internalIdMapping));

        assertEquals("User:12345", authenticationModule.extractKafkaUserName(authData));
    }

    @Test
    void testLookupNumericId_noLookup_noCompatMode() {
        // idCompatMode is by default false in config!
        JSONObject authData = new JSONObject(Map.of("userId", "sa-xy125", "apiKey", "ABC123"));
        Map<String, String> internalIdMapping = Map.of("sa-xy123", "12399", "sa-xy125", "12345");

        when(client.getServiceAccountInternalIds()).thenReturn(Mono.just(internalIdMapping));

        assertEquals("User:sa-xy125", authenticationModule.extractKafkaUserName(authData));
        verify(client, times(0)).getServiceAccountInternalIds();
    }

    @Test
    void testLookupNumericId_noLookup_numericUserId() {
        useIdCompatMode();

        JSONObject authData = new JSONObject(Map.of("userId", "12345", "apiKey", "ABC123"));
        Map<String, String> internalIdMapping = Map.of("sa-xy123", "12399", "sa-xy125", "12346");

        when(client.getServiceAccountInternalIds()).thenReturn(Mono.just(internalIdMapping));

        assertEquals("User:12345", authenticationModule.extractKafkaUserName(authData));
        verify(client, times(0)).getServiceAccountInternalIds();
    }

    @Test
    void testLookupNumericId_noLookup_explicitNumericId() {
        useIdCompatMode();

        JSONObject authData = new JSONObject(Map.of("userId", "sa-xy123", "apiKey", "ABC123", "numericId", "12345"));
        Map<String, String> internalIdMapping = Map.of("sa-xy123", "12399", "sa-xy125", "12346");

        when(client.getServiceAccountInternalIds()).thenReturn(Mono.just(internalIdMapping));

        assertEquals("User:12345", authenticationModule.extractKafkaUserName(authData));
        verify(client, times(0)).getServiceAccountInternalIds();
    }

    @Test
    void testLookupNumericId_noUseOfNumericId() {
        // idCompatMode is by default false in config!
        JSONObject authData = new JSONObject(Map.of("userId", "sa-xy123", "apiKey", "ABC123", "numericId", "12345"));
        Map<String, String> internalIdMapping = Map.of("sa-xy123", "12399", "sa-xy125", "12346");

        when(client.getServiceAccountInternalIds()).thenReturn(Mono.just(internalIdMapping));

        assertEquals("User:sa-xy123", authenticationModule.extractKafkaUserName(authData));
        verify(client, times(0)).getServiceAccountInternalIds();
    }

    @Test
    void testDevAuth_positive() throws Exception {
        Instant now = Instant.now();
        // make sure that there really is slight delay
        Thread.sleep(100);
        enableDeveloperAuthentication();

        ServiceAccountSpec testServiceAccount = new ServiceAccountSpec();
        testServiceAccount.setDisplayName("Test Display Name");
        testServiceAccount.setResourceId("sa-xy123");
        testServiceAccount.setDescription("Test description");
        ApiKeySpec apiKey = newApiKey("TESTKEY", "testSecret", "sa-xy123");

        when(client.listServiceAccounts()).thenReturn(Mono.just(List.of()));
        when(client.createServiceAccount("developer-test-user", "DEV_test-user@test.demo"))
                .thenReturn(Mono.just(testServiceAccount));
        when(client.createApiKey("testEnv", "testCluster", "Developer test-user@test.demo", "sa-xy123"))
                .thenReturn(Mono.just(apiKey));

        CreateAuthenticationResult result = authenticationModule
                .createDeveloperAuthentication("test-user@test.demo", new JSONObject()).get();

        JSONObject authData = result.getPublicAuthenticationData();
        Instant expiresAt = ConfluentCloudAuthUtil.getExpiresAt(authData.toString());
        if (expiresAt == null) {
            fail("No expiry date found in created developer authentication");
            return;
        }

        assertTrue(now.plus(7, ChronoUnit.DAYS).isBefore(expiresAt));
        assertTrue(now.plus(8, ChronoUnit.DAYS).isAfter(expiresAt));
        assertEquals("sa-xy123", authData.getString("userId"));
        assertEquals("TESTKEY", authData.getString("apiKey"));

        verify(client, times(1)).createServiceAccount("developer-test-user", "DEV_test-user@test.demo");
        verify(client, times(1)).createApiKey("testEnv", "testCluster", "Developer test-user@test.demo", "sa-xy123");
    }

    @Test
    void testDevAuth_notEnabled() throws Exception {
        ServiceAccountSpec testServiceAccount = new ServiceAccountSpec();
        testServiceAccount.setDisplayName("Test Display Name");
        testServiceAccount.setResourceId("sa-xy123");
        testServiceAccount.setDescription("Test description");
        ApiKeySpec apiKey = newApiKey("TESTKEY", "testSecret", "sa-xy123");

        when(client.listServiceAccounts()).thenReturn(Mono.just(List.of()));
        when(client.createServiceAccount("developer-test-user", "DEV_test-user@test.demo"))
                .thenReturn(Mono.just(testServiceAccount));
        when(client.createApiKey("testEnv", "testCluster", "Developer test-user@test.demo", "sa-xy123"))
                .thenReturn(Mono.just(apiKey));

        try {
            authenticationModule.createDeveloperAuthentication("test-user@test.demo", new JSONObject()).get();
            fail("Expected an exception when developer auth is not enabled");
        }
        catch (ExecutionException e) {
            // OK
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }

    @Test
    public void testShortenAppNames() throws Exception {
        ServiceAccountSpec testServiceAccount = new ServiceAccountSpec();
        testServiceAccount.setDisplayName("Test Display Name");
        testServiceAccount.setResourceId("sa-xy123");
        testServiceAccount.setDescription("Test description");
        ApiKeySpec apiKey = newApiKey("TESTKEY", "testSecret", "sa-xy123");

        when(client.listServiceAccounts()).thenReturn(Mono.just(List.of()));
        ArgumentCaptor<String> displayNameCaptor = ArgumentCaptor.forClass(String.class);
        when(client.createServiceAccount(displayNameCaptor.capture(), anyString()))
                .thenReturn(Mono.just(testServiceAccount));
        when(client.createApiKey(anyString(), anyString(), anyString(), anyString())).thenReturn(Mono.just(apiKey));

        authenticationModule.createApplicationAuthentication("app-1",
                "A_very_long_strange_application_name_which_likely_exceeds_50_characters_whoever_names_such_applications",
                new JSONObject()).get();

        assertTrue(displayNameCaptor.getValue().length() <= 64);
    }

}
