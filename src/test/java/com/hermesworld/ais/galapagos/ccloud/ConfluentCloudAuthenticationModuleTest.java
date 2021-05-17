package com.hermesworld.ais.galapagos.ccloud;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ApiKeyInfo;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ConfluentApiClient;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ServiceAccountInfo;
import com.hermesworld.ais.galapagos.ccloud.auth.ConfluentCloudAuthConfig;
import com.hermesworld.ais.galapagos.ccloud.auth.ConfluentCloudAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import org.hamcrest.core.StringContains;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ConfluentCloudAuthenticationModuleTest {

    private KafkaAuthenticationModule authenticationModule;

    private ConfluentApiClient client;

    @BeforeEach
    public void init() {
        authenticationModule = new ConfluentCloudAuthenticationModule(new ConfluentCloudAuthConfig());
        client = mock(ConfluentApiClient.class);
    }

    @Test
    public void extractKafkaUserNameTest_positive() {
        String kafkaUserName = authenticationModule.extractKafkaUserName("test", new JSONObject("{userId:1234}"));

        assertEquals("User:1234", kafkaUserName);
    }

    @Test
    public void extractKafkaUserNameTest_negative() {
        assertThrows(JSONException.class,
                () -> authenticationModule.extractKafkaUserName("test", new JSONObject("{}")));
    }

    @Test
    public void fillCorrectProps_positive() {
        ConfluentCloudAuthConfig config = new ConfluentCloudAuthConfig();
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
    public void fillCorrectProps_negative() {
        Properties props = new Properties();
        authenticationModule.addRequiredKafkaProperties(props);

        assertThat(props.getProperty("sasl.jaas.config"), StringContains.containsString("username='null'"));
        assertThat(props.getProperty("sasl.jaas.config"), StringContains.containsString("password='null'"));
    }

    @Test
    public void deleteApplicationAuthenticationTest_positive()
            throws ExecutionException, InterruptedException {
        ConfluentCloudAuthConfig config = new ConfluentCloudAuthConfig();
        config.setEnvironmentId("testEnv");
        config.setClusterId("testCluster");
        authenticationModule = new ConfluentCloudAuthenticationModule(config);
        ReflectionTestUtils.setField(authenticationModule, "client", client);

        ApiKeyInfo apiKey1 = new ApiKeyInfo();
        apiKey1.setId(1);
        apiKey1.setKey("someKey1");
        apiKey1.setSecret("someSecret1");
        apiKey1.setUserId(123);

        ApiKeyInfo apiKey2 = new ApiKeyInfo();
        apiKey2.setId(2);
        apiKey2.setKey("someKey2");
        apiKey2.setSecret("someSecret2");
        apiKey2.setUserId(324);


        ApplicationMetadata app = new ApplicationMetadata();
        app.setApplicationId("quattro-1");
        app.setAuthenticationJson("{userId:1234,apiKey:someKey1}");

        when(client.listApiKeys("testEnv", "testCluster")).thenReturn(Mono.just(List.of(apiKey1, apiKey2)));
        when(client.deleteApiKey(apiKey1)).thenReturn(Mono.just(true));

        String auth = app.getAuthenticationJson();
        authenticationModule.deleteApplicationAuthentication(app.getApplicationId(), new JSONObject(auth)).get();
        verify(client).deleteApiKey(apiKey1);
    }

    @Test
    public void deleteApplicationAuthenticationTest_negativeNoApiKeyObjectInAuthJson()
            throws ExecutionException, InterruptedException {
        ConfluentCloudAuthConfig config = new ConfluentCloudAuthConfig();
        config.setEnvironmentId("testEnv");
        config.setClusterId("testCluster");
        authenticationModule = new ConfluentCloudAuthenticationModule(config);
        ReflectionTestUtils.setField(authenticationModule, "client", client);

        ApiKeyInfo apiKey1 = new ApiKeyInfo();
        apiKey1.setId(1);
        apiKey1.setKey("someKey1");
        apiKey1.setSecret("someSecret1");
        apiKey1.setUserId(123);

        ApiKeyInfo apiKey2 = new ApiKeyInfo();
        apiKey2.setId(2);
        apiKey2.setKey("someKey2");
        apiKey2.setSecret("someSecret2");
        apiKey2.setUserId(324);

        ApplicationMetadata app = new ApplicationMetadata();
        app.setApplicationId("quattro-1");
        app.setAuthenticationJson("{userId:1234}");

        when(client.listApiKeys("testEnv", "testCluster")).thenReturn(Mono.just(List.of(apiKey1, apiKey2)));

        String auth = app.getAuthenticationJson();
        authenticationModule.deleteApplicationAuthentication(app.getApplicationId(), new JSONObject(auth)).get();
        verify(client, times(0)).deleteApiKey(any());
    }

    @Test
    public void createApplicationAuthenticationTest_createServiceAccForAppThatHasNoAcc()
            throws ExecutionException, InterruptedException {
        ConfluentCloudAuthConfig config = new ConfluentCloudAuthConfig();
        config.setEnvironmentId("testEnv");
        config.setClusterId("testCluster");
        authenticationModule = new ConfluentCloudAuthenticationModule(config);
        ReflectionTestUtils.setField(authenticationModule, "client", client);

        ApiKeyInfo apiKey1 = new ApiKeyInfo();
        apiKey1.setId(1);
        apiKey1.setKey("someKey1");
        apiKey1.setSecret("someSecret1");
        apiKey1.setUserId(123);

        ApiKeyInfo apiKey2 = new ApiKeyInfo();
        apiKey2.setId(2);
        apiKey2.setKey("someKey2");
        apiKey2.setSecret("someSecret2");
        apiKey2.setUserId(324);

        ApiKeyInfo apiKey3 = new ApiKeyInfo();
        apiKey3.setId(3);
        apiKey3.setKey("someKey3");
        apiKey3.setSecret("someSecret3");
        apiKey3.setUserId(143);
        apiKey3.setCreated(Instant.now());

        ApplicationMetadata app = new ApplicationMetadata();
        app.setApplicationId("quattro-1");
        app.setAuthenticationJson("{userId:1234}");

        ServiceAccountInfo testServiceAccount = new ServiceAccountInfo();
        testServiceAccount.setId(Integer.valueOf(1));
        testServiceAccount.setEmail("testEmail");
        testServiceAccount.setServiceName("testName");

        when(client.listApiKeys("testEnv", "testCluster")).thenReturn(Mono.just(List.of(apiKey1, apiKey2)));
        when(client.listServiceAccounts()).thenReturn(Mono.just(Collections.emptyList()));
        when(client.isLoggedIn()).thenReturn(true);
        when(client.createServiceAccount("application-normalizedAppNameTest", "APP_quattro-1"))
                .thenReturn(Mono.just(testServiceAccount));
        when(client.createApiKey("testEnv", "testCluster", "Application normalizedAppNameTest", 1))
                .thenReturn(Mono.just(apiKey3));

        String auth = app.getAuthenticationJson();

        authenticationModule.createApplicationAuthentication("quattro-1", "normalizedAppNameTest", new JSONObject(auth))
                .get();

        verify(client, times(1)).createServiceAccount("application-normalizedAppNameTest", "APP_quattro-1");
        verify(client, times(1)).createApiKey("testEnv", "testCluster", "Application normalizedAppNameTest", 1);
    }

    @Test
    public void createApplicationAuthenticationTest_reuseServiceAccIfExists()
            throws ExecutionException, InterruptedException {
        ConfluentCloudAuthConfig config = new ConfluentCloudAuthConfig();
        config.setEnvironmentId("testEnv");
        config.setClusterId("testCluster");
        authenticationModule = new ConfluentCloudAuthenticationModule(config);
        ReflectionTestUtils.setField(authenticationModule, "client", client);

        ApiKeyInfo apiKey1 = new ApiKeyInfo();
        apiKey1.setId(1);
        apiKey1.setKey("someKey1");
        apiKey1.setSecret("someSecret1");
        apiKey1.setUserId(123);

        ApiKeyInfo apiKey2 = new ApiKeyInfo();
        apiKey2.setId(2);
        apiKey2.setKey("someKey2");
        apiKey2.setSecret("someSecret2");
        apiKey2.setUserId(324);

        ApiKeyInfo apiKey3 = new ApiKeyInfo();
        apiKey3.setId(3);
        apiKey3.setKey("someKey3");
        apiKey3.setSecret("someSecret3");
        apiKey3.setUserId(143);
        apiKey3.setCreated(Instant.now());

        ApplicationMetadata app = new ApplicationMetadata();
        app.setApplicationId("quattro-1");
        app.setAuthenticationJson("{userId:1234}");

        ServiceAccountInfo testServiceAccount = new ServiceAccountInfo();
        testServiceAccount.setId(Integer.valueOf(1));
        testServiceAccount.setEmail("testEmail");
        testServiceAccount.setServiceName("testName");
        testServiceAccount.setServiceDescription("APP_quattro-1");

        when(client.listApiKeys("testEnv", "testCluster")).thenReturn(Mono.just(List.of(apiKey1, apiKey2)));
        when(client.listServiceAccounts()).thenReturn(Mono.just(List.of(testServiceAccount)));
        when(client.isLoggedIn()).thenReturn(true);
        when(client.createApiKey("testEnv", "testCluster", "Application normalizedAppNameTest", 1))
                .thenReturn(Mono.just(apiKey3));

        String auth = app.getAuthenticationJson();

        authenticationModule.createApplicationAuthentication("quattro-1", "normalizedAppNameTest", new JSONObject(auth))
                .get();

        verify(client, times(0)).createServiceAccount(anyString(), anyString());
        verify(client, times(1)).createApiKey("testEnv", "testCluster", "Application normalizedAppNameTest", 1);
    }

    @Test
    public void updateApplicationAuthenticationTest() throws ExecutionException, InterruptedException {
        ConfluentCloudAuthConfig config = new ConfluentCloudAuthConfig();
        config.setEnvironmentId("testEnv");
        config.setClusterId("testCluster");
        authenticationModule = new ConfluentCloudAuthenticationModule(config);
        ReflectionTestUtils.setField(authenticationModule, "client", client);

        ApiKeyInfo apiKey1 = new ApiKeyInfo();
        apiKey1.setId(1);
        apiKey1.setKey("someKey1");
        apiKey1.setSecret("someSecret1");
        apiKey1.setUserId(123);

        ApiKeyInfo apiKey2 = new ApiKeyInfo();
        apiKey2.setId(2);
        apiKey2.setKey("someKey2");
        apiKey2.setSecret("someSecret2");
        apiKey2.setUserId(324);

        ApiKeyInfo apiKey3 = new ApiKeyInfo();
        apiKey3.setId(3);
        apiKey3.setKey("someKey3");
        apiKey3.setSecret("someSecret3");
        apiKey3.setUserId(143);
        apiKey3.setCreated(Instant.now());

        ApplicationMetadata app = new ApplicationMetadata();
        app.setApplicationId("quattro-1");
        app.setAuthenticationJson("{userId:1234,apiKey:someKey1}");

        ServiceAccountInfo testServiceAccount = new ServiceAccountInfo();
        testServiceAccount.setId(Integer.valueOf(1));
        testServiceAccount.setEmail("testEmail");
        testServiceAccount.setServiceName("testName");
        testServiceAccount.setServiceDescription("APP_quattro-1");

        when(client.listApiKeys("testEnv", "testCluster")).thenReturn(Mono.just(List.of(apiKey1, apiKey2)));
        when(client.listServiceAccounts()).thenReturn(Mono.just(List.of(testServiceAccount)));
        when(client.isLoggedIn()).thenReturn(true);
        when(client.deleteApiKey(apiKey1)).thenReturn(Mono.just(true));
        when(client.createApiKey("testEnv", "testCluster", "Application normalizedAppNameTest", 1))
                .thenReturn(Mono.just(apiKey3));

        String auth = app.getAuthenticationJson();

        authenticationModule.updateApplicationAuthentication("quattro-1", "normalizedAppNameTest", new JSONObject(),
                new JSONObject(auth)).get();

        verify(client).deleteApiKey(apiKey1);
        verify(client, times(1)).createApiKey("testEnv", "testCluster", "Application normalizedAppNameTest", 1);
    }

}
