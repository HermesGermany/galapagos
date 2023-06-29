package com.hermesworld.ais.galapagos.applications;

import com.hermesworld.ais.galapagos.applications.controller.ApplicationsController;
import com.hermesworld.ais.galapagos.applications.controller.CertificateRequestDto;
import com.hermesworld.ais.galapagos.applications.controller.CertificateResponseDto;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.staging.Staging;
import com.hermesworld.ais.galapagos.staging.StagingResult;
import com.hermesworld.ais.galapagos.staging.StagingService;
import com.hermesworld.ais.galapagos.staging.impl.StagingServiceImpl;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApplicationsControllerTest {

    private final ApplicationsService applicationsService = mock(ApplicationsService.class);

    private final StagingService stagingService = mock(StagingService.class);

    private final KafkaClusters kafkaClusters = mock(KafkaClusters.class);

    @Test
    void testUpdateApplicationCertificateDependentOnStageName() {
        // Arrange
        String applicationId = "testapp-1";
        String environmentId = "devtest";
        CertificateRequestDto certificateRequestDto = new CertificateRequestDto();
        certificateRequestDto.setGenerateKey(true);

        KnownApplication knownApp = mock(KnownApplication.class);
        when(knownApp.getName()).thenReturn("TestApp");
        KafkaEnvironmentConfig kafkaEnvironmentConfig = mock(KafkaEnvironmentConfig.class);

        ApplicationsController controller = new ApplicationsController(applicationsService, stagingService,
                kafkaClusters);
        when(applicationsService.getKnownApplication(any())).thenReturn(Optional.of(knownApp));
        when(applicationsService.isUserAuthorizedFor(any())).thenReturn(true);
        when(kafkaClusters.getEnvironmentMetadata(environmentId)).thenReturn(Optional.of(kafkaEnvironmentConfig));

        when(applicationsService.registerApplicationOnEnvironment(any(), any(), any(), any())).then(inv -> {
            OutputStream os = inv.getArgument(3);
            os.write(new byte[] { 1, 2, 3, 4 });
            os.flush();
            return FutureUtil.noop();
        });

        // Act
        CertificateResponseDto testee = controller.updateApplicationCertificate(applicationId, environmentId,
                certificateRequestDto);

        // Assert
        assertEquals("testapp_devtest.p12", testee.getFileName());
    }

    @Test
    void testStagingWithoutSchema_include_failure() throws Exception {
        TopicService topicService = mock(TopicService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        KafkaEnvironmentConfig env1 = mock(KafkaEnvironmentConfig.class);
        KafkaEnvironmentConfig env2 = mock(KafkaEnvironmentConfig.class);
        when(env1.getId()).thenReturn("dev");
        when(env2.getId()).thenReturn("test");
        List<? extends KafkaEnvironmentConfig> ls = List.of(env1, env2);

        doReturn(ls).when(kafkaClusters).getEnvironmentsMetadata();

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("app1.internal.topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        TopicMetadata topic2 = new TopicMetadata();
        topic2.setName("app1.internal.topic-2");
        topic2.setOwnerApplicationId("app-1");
        topic2.setType(TopicType.EVENTS);

        SchemaMetadata schema1 = new SchemaMetadata();
        schema1.setId("schema-1");
        schema1.setTopicName("app1.internal.topic-2");
        schema1.setJsonSchema("{ }");
        schema1.setSchemaVersion(1);

        ApplicationMetadata appMetadata = new ApplicationMetadata();
        appMetadata.setApplicationId("app-1");
        appMetadata.setInternalTopicPrefixes(List.of("app1.internal."));

        when(topicService.listTopics("dev")).thenReturn(List.of(topic1, topic2));
        when(topicService.getTopicSchemaVersions("dev", "app1.internal.topic-2")).thenReturn(List.of(schema1));
        when(topicService.buildTopicCreateParams("dev", "app1.internal.topic-2"))
                .thenReturn(CompletableFuture.completedFuture(new TopicCreateParams(2, 1)));
        when(topicService.createTopic(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(topicService.addTopicSchemaVersion(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        when(applicationsService.isUserAuthorizedFor("app-1")).thenReturn(true);
        when(applicationsService.getApplicationMetadata("test", "app-1")).thenReturn(Optional.of(appMetadata));

        StagingService stagingService = new StagingServiceImpl(kafkaClusters, applicationsService, topicService,
                subscriptionService);

        ApplicationsController controller = new ApplicationsController(applicationsService, stagingService,
                kafkaClusters);
        Staging staging = controller.describeStaging("dev", "app-1");

        assertEquals(2, staging.getChanges().size());

        // must not succeed for first topic because no schema for API topic
        List<StagingResult> result = controller.performStaging("dev", "app-1",
                JsonUtil.newObjectMapper().writeValueAsString(staging.getChanges()));
        assertEquals(2, result.size());
        assertFalse(result.get(0).isStagingSuccessful());
        assertTrue(result.get(1).isStagingSuccessful());
    }

}
