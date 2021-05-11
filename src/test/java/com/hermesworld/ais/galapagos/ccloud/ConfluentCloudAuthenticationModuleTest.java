package com.hermesworld.ais.galapagos.ccloud;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ApiKeyInfo;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ConfluentApiClient;
import com.hermesworld.ais.galapagos.ccloud.auth.ConfluentCloudAuthConfig;
import com.hermesworld.ais.galapagos.ccloud.auth.ConfluentCloudAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.auth.CreateAuthenticationResult;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import org.hamcrest.core.StringContains;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfluentCloudAuthenticationModuleTest {

    private KafkaClusters kafkaClusters;

    private final TopicBasedRepository<ApplicationMetadata> applicationMetadataRepository = new TopicBasedRepositoryMock<>();

    private KafkaAuthenticationModule authenticationModule;

    private ConfluentApiClient client;

    @BeforeEach
    public void feedMocks() {
        kafkaClusters = mock(KafkaClusters.class);

        KafkaCluster testCluster = mock(KafkaCluster.class);
        when(testCluster.getRepository("application-metadata", ApplicationMetadata.class))
                .thenReturn(applicationMetadataRepository);

        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));

        CreateAuthenticationResult authResult = new CreateAuthenticationResult(
                new JSONObject(Map.of("testentry", true)), new byte[] { 1 });

        when(authenticationModule.createApplicationAuthentication(eq("quattro-1"), eq("quattro"), any()))
                .thenReturn(CompletableFuture.completedFuture(authResult));
        when(kafkaClusters.getAuthenticationModule("test")).thenReturn(Optional.of(authenticationModule));
    }

    @Test
    public void extractKafkaUserNameTest_positive() throws ExecutionException, InterruptedException {
        authenticationModule = new ConfluentCloudAuthenticationModule(new ConfluentCloudAuthConfig());

        ApplicationMetadata app = new ApplicationMetadata();
        app.setApplicationId("quattro-1");
        app.setAuthenticationJson("{userId:1234}");
        applicationMetadataRepository.save(app).get();

        String auth = app.getAuthenticationJson();
        String kafkaUserName = authenticationModule.extractKafkaUserName("test", new JSONObject(auth));

        assertEquals("User:1234", kafkaUserName);

    }

    @Test
    public void extractKafkaUserNameTest_negative() throws ExecutionException, InterruptedException {
        authenticationModule = new ConfluentCloudAuthenticationModule(new ConfluentCloudAuthConfig());

        ApplicationMetadata app = new ApplicationMetadata();
        app.setApplicationId("quattro-1");
        app.setAuthenticationJson("{}");
        applicationMetadataRepository.save(app).get();

        String auth = app.getAuthenticationJson();

        assertThrows(JSONException.class,
                () -> authenticationModule.extractKafkaUserName("test", new JSONObject(auth)));
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
        assertThat(props.getProperty("sasl.jaas.config"), StringContains.containsString("someApiKey"));
        assertThat(props.getProperty("sasl.jaas.config"), StringContains.containsString("secretPassword"));

    }

    @Test
    public void fillCorrectProps_negative() {
        ConfluentCloudAuthConfig config = new ConfluentCloudAuthConfig();
        authenticationModule = new ConfluentCloudAuthenticationModule(config);

        Properties props = new Properties();
        authenticationModule.addRequiredKafkaProperties(props);

        assertFalse(props.getProperty("sasl.jaas.config").contains("someApiKey"));
        assertFalse(props.getProperty("sasl.jaas.config").contains("secretPassword"));

    }

    @Test
    public void createApplicationAuthenticationTest_createServiceAccForAppThatHasNoAcc() {

    }

    @Test
    public void deleteApplicationAuthenticationTest_positive() throws ExecutionException, InterruptedException {

        client = mock(ConfluentApiClient.class);

        ConfluentCloudAuthConfig config = new ConfluentCloudAuthConfig();
        config.setEnvironmentId("testEnv");
        config.setClusterId("testCluster");
        authenticationModule = new ConfluentCloudAuthenticationModule(config);

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
        applicationMetadataRepository.save(app).get();

        when(client.listApiKeys("testEnv", "testCluster")).thenReturn(Mono.just(List.of(apiKey1, apiKey2)));

        String auth = app.getAuthenticationJson();
        authenticationModule.deleteApplicationAuthentication(app.getApplicationId(), new JSONObject(auth)).get();

        // test still in work...
    }

}
