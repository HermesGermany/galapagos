package com.hermesworld.ais.galapagos.topics.service.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.applications.impl.KnownApplicationImpl;
import com.hermesworld.ais.galapagos.events.GalapagosEventManagerMock;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.naming.InvalidTopicNameException;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.schemas.IncompatibleSchemaException;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.*;
import com.hermesworld.ais.galapagos.topics.config.GalapagosTopicConfig;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TopicServiceImplTest {

    @MockBean
    private KafkaClusters kafkaClusters;

    private ApplicationsService applicationsService;

    private NamingService namingService;

    private CurrentUserService userService;

    private GalapagosTopicConfig topicConfig;

    private GalapagosEventManagerMock eventManager;

    private KafkaCluster kafkaTestCluster;

    private TopicBasedRepositoryMock<TopicMetadata> topicRepository;

    private TopicBasedRepositoryMock<SchemaMetadata> schemaRepository;

    @BeforeEach
    void feedMocks() {
        kafkaClusters = mock(KafkaClusters.class);
        applicationsService = mock(ApplicationsService.class);
        namingService = mock(NamingService.class);
        userService = mock(CurrentUserService.class);
        topicConfig = mock(GalapagosTopicConfig.class);
        eventManager = new GalapagosEventManagerMock();

        kafkaTestCluster = mock(KafkaCluster.class);
        topicRepository = new TopicBasedRepositoryMock<>();
        schemaRepository = new TopicBasedRepositoryMock<>();
        when(kafkaTestCluster.getId()).thenReturn("test");
        when(kafkaTestCluster.getRepository("topics", TopicMetadata.class)).thenReturn(topicRepository);
        when(kafkaTestCluster.getRepository("schemas", SchemaMetadata.class)).thenReturn(schemaRepository);
        when(kafkaTestCluster.getActiveBrokerCount()).thenReturn(CompletableFuture.completedFuture(5));

        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(kafkaTestCluster));

        when(userService.getCurrentUserName()).thenReturn(Optional.of("testuser"));

        ApplicationMetadata app1 = new ApplicationMetadata();
        app1.setApplicationId("app-1");
        when(applicationsService.getApplicationMetadata("test", "app-1")).thenReturn(Optional.of(app1));

        KnownApplication kapp1 = new KnownApplicationImpl("app-1", "App 1");
        when(applicationsService.getKnownApplication("app-1")).thenReturn(Optional.of(kapp1));
        when(applicationsService.isUserAuthorizedFor("app-1")).thenReturn(true);

        when(topicConfig.getMaxPartitionCount()).thenReturn(10);
        when(topicConfig.getDefaultPartitionCount()).thenReturn(6);
        when(topicConfig.getStandardReplicationFactor()).thenReturn(2);
        when(topicConfig.getCriticalReplicationFactor()).thenReturn(4);
    }

    @Test
    void testCreateTopic_positive() throws Exception {
        List<InvocationOnMock> createInvs = new ArrayList<>();

        when(kafkaTestCluster.createTopic(any(), any())).then(inv -> {
            createInvs.add(inv);
            return FutureUtil.noop();
        });

        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setDescription("Desc");
        topic1.setOwnerApplicationId("app-1");
        topic1.setSubscriptionApprovalRequired(true);
        topic1.setType(TopicType.EVENTS);

        service.createTopic("test", topic1, 8, Map.of("some.property", "some.value")).get();

        assertEquals(1, createInvs.size());

        @SuppressWarnings("OptionalGetWithoutIsPresent")
        TopicMetadata savedTopic = topicRepository.getObject("topic-1").get();
        assertEquals("Desc", savedTopic.getDescription());
        assertEquals("app-1", savedTopic.getOwnerApplicationId());
        assertEquals(TopicType.EVENTS, savedTopic.getType());
        assertTrue(savedTopic.isSubscriptionApprovalRequired());

        assertEquals("topic-1", createInvs.get(0).getArgument(0));
        TopicCreateParams params = createInvs.get(0).getArgument(1);
        assertEquals(2, params.getReplicationFactor());
        assertEquals(8, params.getNumberOfPartitions());
        assertEquals("some.value", params.getTopicConfigs().get("some.property"));

        assertEquals(1, eventManager.getSinkInvocations().size());
        assertEquals("handleTopicCreated", eventManager.getSinkInvocations().get(0).getMethod().getName());
    }

    @Test
    void testCreateTopic_downToMaxPartitions() throws Exception {
        List<InvocationOnMock> createInvs = new ArrayList<>();

        when(kafkaTestCluster.createTopic(any(), any())).then(inv -> {
            createInvs.add(inv);
            return FutureUtil.noop();
        });

        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setDescription("Desc");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        service.createTopic("test", topic1, 14, Map.of()).get();

        assertEquals(1, createInvs.size());

        TopicCreateParams params = createInvs.get(0).getArgument(1);

        // must be set to default partitions (see feedMocks)
        assertEquals(6, params.getNumberOfPartitions());
    }

    @Test
    void testCreateTopic_criticalReplicationFactor() throws Exception {
        List<InvocationOnMock> createInvs = new ArrayList<>();

        when(kafkaTestCluster.createTopic(any(), any())).then(inv -> {
            createInvs.add(inv);
            return FutureUtil.noop();
        });

        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setDescription("Desc");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);
        topic1.setCriticality(Criticality.CRITICAL);

        service.createTopic("test", topic1, 3, Map.of()).get();

        assertEquals(1, createInvs.size());

        TopicCreateParams params = createInvs.get(0).getArgument(1);

        // must be set to configured critical replication factor
        assertEquals(4, params.getReplicationFactor());
    }

    @Test
    void testCreateTopic_replicationFactor_downToNumBrokers() throws Exception {
        List<InvocationOnMock> createInvs = new ArrayList<>();

        // 6 is more than the 5 brokers we have, so should be downed to 5
        when(topicConfig.getCriticalReplicationFactor()).thenReturn(6);

        when(kafkaTestCluster.createTopic(any(), any())).then(inv -> {
            createInvs.add(inv);
            return FutureUtil.noop();
        });

        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setDescription("Desc");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);
        topic1.setCriticality(Criticality.CRITICAL);

        service.createTopic("test", topic1, 3, Map.of()).get();

        assertEquals(1, createInvs.size());

        TopicCreateParams params = createInvs.get(0).getArgument(1);

        // must be equal to number of brokers, as higher is not allowed
        assertEquals(5, params.getReplicationFactor());
    }

    @Test
    void testCreateTopic_useDefaultPartitions() throws Exception {
        List<InvocationOnMock> createInvs = new ArrayList<>();

        when(kafkaTestCluster.createTopic(any(), any())).then(inv -> {
            createInvs.add(inv);
            return FutureUtil.noop();
        });

        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setDescription("Desc");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        service.createTopic("test", topic1, null, Map.of()).get();

        assertEquals(1, createInvs.size());

        TopicCreateParams params = createInvs.get(0).getArgument(1);

        // must be set to default partitions (see feedMocks)
        assertEquals(6, params.getNumberOfPartitions());
    }

    @Test
    void testCreateTopic_nameValidationFails() throws Exception {
        List<InvocationOnMock> createInvs = new ArrayList<>();

        when(kafkaTestCluster.createTopic(any(), any())).then(inv -> {
            createInvs.add(inv);
            return FutureUtil.noop();
        });

        doThrow(new InvalidTopicNameException("Invalid!")).when(namingService).validateTopicName(any(), any(), any());

        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setDescription("Desc");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        try {
            service.createTopic("test", topic1, null, Map.of()).get();
            fail("Expected exception when creating topic for which name validation fails");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof InvalidTopicNameException);
        }

        assertEquals(0, createInvs.size());
    }

    @Test
    @DisplayName("should add producer to topic")
    void addTopicProducerTest_positive() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);
        topicRepository.save(topic1);

        service.addTopicProducer("test", "topic-1", "producer1").get();

        TopicMetadata savedTopic = topicRepository.getObject("topic-1").get();

        assertFalse(savedTopic.getProducers().isEmpty());
        assertEquals("producer1", savedTopic.getProducers().get(0));

    }

    @Test
    @DisplayName("should fail adding a producer to commands topic")
    void addTopicProducerTest_negative() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.COMMANDS);
        topicRepository.save(topic1).get();

        try {
            service.addTopicProducer("test", "topic-1", "producer1").get();
            fail("Expected exception when adding a producer to commands topic");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }

    }

    @Test
    @DisplayName("should delete producer from topic")
    void deleteTopicProducersTest_positive() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setProducers(List.of("producer1", "producer2", "producer3", "producer4"));
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);
        topicRepository.save(topic1).get();

        service.removeTopicProducer("test", "topic-1", "producer3").get();

        TopicMetadata savedTopic = topicRepository.getObject("topic-1").get();

        assertEquals(3, savedTopic.getProducers().size());
        assertFalse(savedTopic.getProducers().contains("producer3"));

    }

    @Test
    @DisplayName("should not be able to delete producer from commands topic")
    void deleteTopicProducersTest_negative() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setProducers(List.of("producer1", "producer2", "producer3", "producer4"));
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.COMMANDS);
        topicRepository.save(topic1).get();

        try {
            service.removeTopicProducer("test", "topic-1", "producer3").get();
            fail("Expected exception when deleting producer from commands topic");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
            TopicMetadata savedTopic = topicRepository.getObject("topic-1").get();
            assertEquals(4, savedTopic.getProducers().size());
            assertTrue(savedTopic.getProducers().contains("producer3"));
        }

    }

    @Test
    @DisplayName("should promote a producer to new Topic owner")
    void changeOwnerOfTopicTest_positive() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);
        topic1.setProducers(List.of("producer1", "producer2", "producer3", "producer4"));
        topicRepository.save(topic1).get();
        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("test", "test2", "test3"));

        service.changeTopicOwner("test", "topic-1", "producer1").get();

        TopicMetadata savedTopic = topicRepository.getObject("topic-1").get();

        assertEquals(4, savedTopic.getProducers().size());
        assertEquals("producer1", savedTopic.getOwnerApplicationId());
        assertTrue(savedTopic.getProducers().contains("app-1"));
    }

    @Test
    @DisplayName("should not promote a producer to new Topic owner for internal topics")
    void changeOwnerOfTopicTest_negative() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.INTERNAL);
        topic1.setProducers(List.of("producer1", "producer2", "producer3", "producer4"));
        topicRepository.save(topic1).get();
        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("test", "test2", "test3"));

        try {
            service.changeTopicOwner("test", "topic-1", "producer1").get();
            fail("exception expected when trying no change owner of internal topic");
        }
        catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }

    }

    @Test
    void testDeleteLatestSchemaVersion() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        topicRepository.save(topic1).get();

        SchemaMetadata schema1 = new SchemaMetadata();
        schema1.setId("1234");
        schema1.setTopicName("topic-1");
        schema1.setCreatedBy("otheruser");
        schema1.setJsonSchema(buildJsonSchema(List.of("propA"), List.of("string")));
        schema1.setSchemaVersion(1);

        SchemaMetadata latestSchema = new SchemaMetadata();
        latestSchema.setId("9999");
        latestSchema.setTopicName("topic-1");
        latestSchema.setCreatedBy("testuser");
        latestSchema.setJsonSchema(buildJsonSchema(List.of("propA", "propB"), List.of("string", "string")));
        latestSchema.setSchemaVersion(2);

        schemaRepository.save(schema1).get();
        schemaRepository.save(latestSchema).get();

        service.deleteLatestTopicSchemaVersion("test", "topic-1").get();
        assertFalse(schemaRepository.getObject(latestSchema.getId()).isPresent());
    }

    @Test
    void testDeleteLatestSchemaVersionStaged_negative() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);
        KafkaCluster prodCluster = mock(KafkaCluster.class);
        when(kafkaClusters.getEnvironment("prod")).thenReturn(Optional.of(prodCluster));
        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("test", "prod"));

        TopicBasedRepositoryMock<TopicMetadata> prodTopicRepository = new TopicBasedRepositoryMock<>();
        TopicBasedRepositoryMock<SchemaMetadata> prodSchemaRepository = new TopicBasedRepositoryMock<>();
        when(prodCluster.getRepository("topics", TopicMetadata.class)).thenReturn(prodTopicRepository);
        when(prodCluster.getRepository("schemas", SchemaMetadata.class)).thenReturn(prodSchemaRepository);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        topicRepository.save(topic1).get();
        prodTopicRepository.save(topic1).get();

        SchemaMetadata schema = new SchemaMetadata();
        schema.setId("1234");
        schema.setTopicName("topic-1");
        schema.setCreatedBy("otheruser");
        schema.setJsonSchema(buildJsonSchema(List.of("propA"), List.of("string")));
        schema.setSchemaVersion(1);

        schemaRepository.save(schema).get();
        prodSchemaRepository.save(schema).get();

        try {
            service.deleteLatestTopicSchemaVersion("test", "topic-1").get();
            fail("Exception expected, but none thrown");
        }
        catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }

        assertTrue(schemaRepository.getObject(schema.getId()).isPresent());
    }

    @Test
    void testDeleteLatestSchemaVersionWithSubscriber_negative() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        topicRepository.save(topic1).get();

        SchemaMetadata schema = new SchemaMetadata();
        schema.setId("1234");
        schema.setTopicName("topic-1");
        schema.setCreatedBy("otheruser");
        schema.setJsonSchema(buildJsonSchema(List.of("propA"), List.of("string")));
        schema.setSchemaVersion(1);

        SubscriptionMetadata subscription = new SubscriptionMetadata();
        subscription.setId("50");
        subscription.setTopicName("topic-1");
        subscription.setClientApplicationId("2");

        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        when(subscriptionService.getSubscriptionsForTopic("test", "topic-1", false)).thenReturn(List.of(subscription));

        ValidatingTopicServiceImpl validatingService = new ValidatingTopicServiceImpl(service, subscriptionService,
                applicationsService, kafkaClusters, topicConfig, false);

        schemaRepository.save(schema).get();

        try {
            validatingService.deleteLatestTopicSchemaVersion("test", "topic-1").get();
            fail("Exception expected, but none thrown");
        }
        catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }

        assertTrue(schemaRepository.getObject(schema.getId()).isPresent());
    }

    @Test
    void testAddSchemaVersion_sameSchema() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        topicRepository.save(topic1).get();

        SchemaMetadata schema1 = new SchemaMetadata();
        schema1.setId("1234");
        schema1.setTopicName("topic-1");
        schema1.setCreatedBy("otheruser");
        schema1.setJsonSchema(buildJsonSchema(List.of("propA"), List.of("string")));
        schema1.setSchemaVersion(1);

        schemaRepository.save(schema1).get();

        String newSchema = buildJsonSchema(List.of("propA"), List.of("string"));

        try {
            service.addTopicSchemaVersion("test", "topic-1", newSchema, null, SchemaCompatCheckMode.CHECK_SCHEMA).get();
            fail("addTopicSchemaVersion() should have failed because same schema should not be added again");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    void testAddSchemaVersion_incompatibleSchema() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        topicRepository.save(topic1).get();

        SchemaMetadata schema1 = new SchemaMetadata();
        schema1.setId("1234");
        schema1.setTopicName("topic-1");
        schema1.setCreatedBy("otheruser");
        schema1.setJsonSchema(buildJsonSchema(List.of("propA"), List.of("string")));
        schema1.setSchemaVersion(1);

        schemaRepository.save(schema1).get();

        String newSchema = buildJsonSchema(List.of("propB"), List.of("integer"));

        try {
            service.addTopicSchemaVersion("test", "topic-1", newSchema, null, SchemaCompatCheckMode.CHECK_SCHEMA).get();
            fail("addTopicSchemaVersion() should have failed for incompatible schema");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IncompatibleSchemaException);
        }
    }

    @Test
    @DisplayName("should not to check for compatibility if skipCompatCheck is set to true")
    void testAddSchemaVersion_skipCompatibleSchemaCheckForAdmins() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topicRepository.save(topic1).get();

        SchemaMetadata schema1 = new SchemaMetadata();
        schema1.setTopicName("topic-1");
        schema1.setJsonSchema(buildJsonSchema(List.of("propA"), List.of("string")));
        schema1.setSchemaVersion(1);
        schemaRepository.save(schema1).get();

        when(userService.isAdmin()).thenReturn(true);

        String newSchema = buildJsonSchema(List.of("propB"), List.of("integer"));
        service.addTopicSchemaVersion("test", "topic-1", newSchema, "some change decs",
                SchemaCompatCheckMode.SKIP_SCHEMA_CHECK).get();
    }

    @Test
    void testAddSchemaVersion_withMetadata() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        topicRepository.save(topic1).get();

        SchemaMetadata schema1 = new SchemaMetadata();
        schema1.setId("1234");
        schema1.setTopicName("topic-1");
        schema1.setCreatedBy("otheruser");
        schema1.setJsonSchema(buildJsonSchema(List.of("propA"), List.of("string")));
        schema1.setSchemaVersion(1);

        schemaRepository.save(schema1).get();

        SchemaMetadata schema2 = new SchemaMetadata();
        schema2.setId("9999");
        schema2.setTopicName("topic-1");
        schema2.setCreatedBy("testuser");
        schema2.setJsonSchema(buildJsonSchema(List.of("propA", "propB"), List.of("string", "string")));
        schema2.setSchemaVersion(2);
        schema2.setChangeDescription("some nice description :)");

        SchemaMetadata newSchemaMetadata = service
                .addTopicSchemaVersion("test", schema2, SchemaCompatCheckMode.CHECK_SCHEMA).get();
        assertEquals("9999", newSchemaMetadata.getId());
        assertEquals(2, newSchemaMetadata.getSchemaVersion());
        assertTrue(newSchemaMetadata.getJsonSchema().contains("propB"));
        assertEquals("testuser", schema2.getCreatedBy());
    }

    @Test
    void testAddSchemaVersion_withMetadata_illegalVersionNo_empty() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        topicRepository.save(topic1).get();

        SchemaMetadata schema1 = new SchemaMetadata();
        schema1.setId("1234");
        schema1.setTopicName("topic-1");
        schema1.setCreatedBy("otheruser");
        schema1.setJsonSchema(buildJsonSchema(List.of("propA"), List.of("string")));
        schema1.setSchemaVersion(2);

        try {
            service.addTopicSchemaVersion("test", schema1, SchemaCompatCheckMode.CHECK_SCHEMA).get();
            fail("addTopicSchemaVersion() should have failed because version #2 and no version existing for topic");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    void testAddSchemaVersion_withMetadata_illegalVersionNo_notMatching() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        topicRepository.save(topic1).get();

        SchemaMetadata schema1 = new SchemaMetadata();
        schema1.setId("1234");
        schema1.setTopicName("topic-1");
        schema1.setCreatedBy("otheruser");
        schema1.setJsonSchema(buildJsonSchema(List.of("propA"), List.of("string")));
        schema1.setSchemaVersion(1);

        schemaRepository.save(schema1).get();

        SchemaMetadata schema2 = new SchemaMetadata();
        schema2.setId("1235");
        schema2.setTopicName("topic-1");
        schema2.setCreatedBy("otheruser");
        schema2.setJsonSchema(buildJsonSchema(List.of("propA", "propB"), List.of("string", "string")));
        schema2.setSchemaVersion(3);

        try {
            service.addTopicSchemaVersion("test", schema2, SchemaCompatCheckMode.CHECK_SCHEMA).get();
            fail("addTopicSchemaVersion() should have failed because version #3 and only version #1 existing for topic");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    void testAddSchemaVersion_invalidSchema() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        topicRepository.save(topic1).get();

        try {
            service.addTopicSchemaVersion("test", "topic-1", "{ \"title\": 17 }", null,
                    SchemaCompatCheckMode.CHECK_SCHEMA).get();
            fail("addTopicSchemaVersion() should have failed because JSON is no JSON schema");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    void testAddSchemaVersion_invalidJson() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        topicRepository.save(topic1).get();

        try {
            service.addTopicSchemaVersion("test", "topic-1", "{", null, SchemaCompatCheckMode.CHECK_SCHEMA).get();
            fail("addTopicSchemaVersion() should have failed because no valid JSON");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    void testAddSchemaVersion_DataObjectSimpleAtJSONSchema() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        String testJsonSchema = StreamUtils.copyToString(
                new ClassPathResource("/schema-compatibility/dataObjectSimple.schema.json").getInputStream(),
                StandardCharsets.UTF_8);

        topicRepository.save(topic1).get();

        try {
            service.addTopicSchemaVersion("test", "topic-1", testJsonSchema, null, SchemaCompatCheckMode.CHECK_SCHEMA)
                    .get();
            fail("addTopicSchemaVersion() should have failed because there is a Data-Object in JSON Schema");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    void testAddSchemaVersion_DataObjectNestedAtJSONSchema() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        String testJsonSchema = StreamUtils.copyToString(
                new ClassPathResource("/schema-compatibility/dataObjectNested.schema.json").getInputStream(),
                StandardCharsets.UTF_8);

        topicRepository.save(topic1).get();

        service.addTopicSchemaVersion("test", "topic-1", testJsonSchema, null, SchemaCompatCheckMode.CHECK_SCHEMA)
                .get();
    }

    @Test
    void testAddSchemaVersion_NoSchemaProp() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        String testJsonSchema = StreamUtils.copyToString(
                new ClassPathResource("/schema-compatibility/noSchemaProp.schema.json").getInputStream(),
                StandardCharsets.UTF_8);

        topicRepository.save(topic1).get();

        try {
            service.addTopicSchemaVersion("test", "topic-1", testJsonSchema, null, SchemaCompatCheckMode.CHECK_SCHEMA)
                    .get();
            fail("addTopicSchemaVersion() should have failed because there is no schema prop in JSON Schema");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    void testSetSubscriptionApprovalRequired_positive() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        topicRepository.save(topic1).get();

        service.setSubscriptionApprovalRequiredFlag("test", "topic-1", true).get();

        assertEquals(1, eventManager.getSinkInvocations().size());
        assertEquals("handleTopicSubscriptionApprovalRequiredFlagChanged",
                eventManager.getSinkInvocations().get(0).getMethod().getName());

        topic1 = topicRepository.getObject("topic-1").orElseThrow();
        assertTrue(topic1.isSubscriptionApprovalRequired());

        service.setSubscriptionApprovalRequiredFlag("test", "topic-1", false).get();

        assertEquals(2, eventManager.getSinkInvocations().size());
        assertEquals("handleTopicSubscriptionApprovalRequiredFlagChanged",
                eventManager.getSinkInvocations().get(1).getMethod().getName());

        topic1 = topicRepository.getObject("topic-1").orElseThrow();
        assertFalse(topic1.isSubscriptionApprovalRequired());
    }

    @Test
    void testSetSubscriptionApprovalRequired_internalTopic() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.INTERNAL);

        topicRepository.save(topic1).get();

        try {
            service.setSubscriptionApprovalRequiredFlag("test", "topic-1", true).get();
            fail("Expected exception when trying to set subscriptionApprovalRequired flag on internal topic");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }

        assertEquals(0, eventManager.getSinkInvocations().size());
    }

    @Test
    void testSetSubscriptionApprovalRequired_noop() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.INTERNAL);
        topic1.setSubscriptionApprovalRequired(true);

        topicRepository.save(topic1).get();

        service.setSubscriptionApprovalRequiredFlag("test", "topic-1", true).get();
        assertEquals(0, eventManager.getSinkInvocations().size());
    }

    @Test
    @DisplayName("should stage new owner on all stages immediately")
    void testChangeOwnerStaging() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);
        KafkaCluster testCluster2 = mock(KafkaCluster.class);
        when(testCluster2.getId()).thenReturn("test2");

        TopicBasedRepositoryMock<TopicMetadata> topicRepository2 = new TopicBasedRepositoryMock<>();
        when(testCluster2.getRepository("topics", TopicMetadata.class)).thenReturn(topicRepository2);

        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("test", "test2"));
        when(kafkaClusters.getEnvironment("test2")).thenReturn(Optional.of(testCluster2));

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);
        topic.setProducers(new ArrayList<>(List.of("producer1")));
        topicRepository.save(topic).get();
        topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);
        topicRepository2.save(topic).get();

        service.changeTopicOwner("test", "topic-1", "producer1").get();

        assertEquals("producer1", service.getTopic("test", "topic-1").get().getOwnerApplicationId());
        assertTrue(service.getTopic("test", "topic-1").get().getProducers().contains("app-1"));
        assertEquals("producer1", service.getTopic("test2", "topic-1").get().getOwnerApplicationId());
        assertTrue(service.getTopic("test2", "topic-1").get().getProducers().contains("app-1"));
    }

    @Test
    void testDeprecateTopic_positive() throws Exception {
        KafkaCluster testCluster2 = mock(KafkaCluster.class);
        when(testCluster2.getId()).thenReturn("test2");
        KafkaCluster testCluster3 = mock(KafkaCluster.class);
        when(testCluster3.getId()).thenReturn("test3");

        TopicBasedRepositoryMock<TopicMetadata> topicRepository2 = new TopicBasedRepositoryMock<>();
        TopicBasedRepositoryMock<TopicMetadata> topicRepository3 = new TopicBasedRepositoryMock<>();
        when(testCluster2.getRepository("topics", TopicMetadata.class)).thenReturn(topicRepository2);
        when(testCluster3.getRepository("topics", TopicMetadata.class)).thenReturn(topicRepository3);
        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("test", "test2", "test3"));
        when(kafkaClusters.getEnvironment("test2")).thenReturn(Optional.of(testCluster2));
        when(kafkaClusters.getEnvironment("test3")).thenReturn(Optional.of(testCluster3));

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);
        topicRepository.save(topic).get();
        topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);
        topicRepository2.save(topic).get();

        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        service.markTopicDeprecated("topic-1", "Because test", LocalDate.of(2020, 10, 1)).get();

        assertTrue(service.getTopic("test", "topic-1").map(TopicMetadata::isDeprecated).orElse(false));
        assertTrue(service.getTopic("test2", "topic-1").map(TopicMetadata::isDeprecated).orElse(false));
        assertFalse(service.getTopic("test3", "topic-1").isPresent());
    }

    @Test
    void testDeprecateTopic_noSuchTopic() throws Exception {
        KafkaCluster testCluster2 = mock(KafkaCluster.class);
        when(testCluster2.getId()).thenReturn("test2");
        KafkaCluster testCluster3 = mock(KafkaCluster.class);
        when(testCluster3.getId()).thenReturn("test3");

        TopicBasedRepositoryMock<TopicMetadata> topicRepository2 = new TopicBasedRepositoryMock<>();
        TopicBasedRepositoryMock<TopicMetadata> topicRepository3 = new TopicBasedRepositoryMock<>();
        when(testCluster2.getRepository("topics", TopicMetadata.class)).thenReturn(topicRepository2);
        when(testCluster3.getRepository("topics", TopicMetadata.class)).thenReturn(topicRepository3);

        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("test", "test2", "test3"));
        when(kafkaClusters.getEnvironment("test2")).thenReturn(Optional.of(testCluster2));
        when(kafkaClusters.getEnvironment("test3")).thenReturn(Optional.of(testCluster3));

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);
        topicRepository.save(topic).get();
        topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);
        topicRepository2.save(topic).get();

        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        try {
            service.markTopicDeprecated("topic-2", "Because test", LocalDate.of(2020, 10, 1)).get();
            fail("Exception expected when marking not existing topic as deprecated, but succeeded");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof NoSuchElementException);
        }
    }

    @Test
    void testunmarkTopicDeprecated() throws Exception {
        KafkaCluster testCluster2 = mock(KafkaCluster.class);
        when(testCluster2.getId()).thenReturn("test2");
        KafkaCluster testCluster3 = mock(KafkaCluster.class);
        when(testCluster3.getId()).thenReturn("test3");

        TopicBasedRepositoryMock<TopicMetadata> topicRepository2 = new TopicBasedRepositoryMock<>();
        TopicBasedRepositoryMock<TopicMetadata> topicRepository3 = new TopicBasedRepositoryMock<>();
        when(testCluster2.getRepository("topics", TopicMetadata.class)).thenReturn(topicRepository2);
        when(testCluster3.getRepository("topics", TopicMetadata.class)).thenReturn(topicRepository3);

        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("test", "test2", "test3"));
        when(kafkaClusters.getEnvironment("test2")).thenReturn(Optional.of(testCluster2));
        when(kafkaClusters.getEnvironment("test3")).thenReturn(Optional.of(testCluster3));

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setDeprecated(true);
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);
        topicRepository.save(topic).get();

        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        service.unmarkTopicDeprecated("topic-1").get();

        assertFalse(service.getTopic("test", "topic-1").get().isDeprecated());
    }

    @Test
    void testChangeDescOfTopic() throws Exception {

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setDescription("this topic is not a nice one :(");
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);
        topicRepository.save(topic).get();

        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        service.updateTopicDescription("test", "topic-1", "this topic is now a nice one :)").get();
        TopicMetadata savedTopic = topicRepository.getObject("topic-1").get();

        assertEquals("this topic is now a nice one :)", savedTopic.getDescription());

    }

    @Test
    void testAddSchemaVersion_DataObjectNestedAtJSONSchemaAndDataTopic() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.DATA);

        String testJsonSchema = StreamUtils.copyToString(
                new ClassPathResource("/schema-compatibility/dataObjectNested.schema.json").getInputStream(),
                StandardCharsets.UTF_8);

        topicRepository.save(topic1).get();

        service.addTopicSchemaVersion("test", "topic-1", testJsonSchema, null, SchemaCompatCheckMode.CHECK_SCHEMA)
                .get();
    }

    @Test
    void testAddSchemaVersion_WithChangeDesc() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.DATA);

        topicRepository.save(topic1).get();

        SchemaMetadata schema1 = new SchemaMetadata();
        schema1.setId("1234");
        schema1.setTopicName("topic-1");
        schema1.setCreatedBy("otheruser");
        schema1.setJsonSchema(buildJsonSchema(List.of("propA"), List.of("string")));
        schema1.setSchemaVersion(1);
        schemaRepository.save(schema1).get();

        SchemaMetadata newSchema = new SchemaMetadata();
        newSchema.setId("9999");
        newSchema.setTopicName("topic-1");
        newSchema.setCreatedBy("testuser");
        newSchema.setJsonSchema(buildJsonSchema(List.of("propA", "propB"), List.of("string", "string")));
        newSchema.setSchemaVersion(2);
        newSchema.setChangeDescription("Added new schema which is better");

        service.addTopicSchemaVersion("test", newSchema, SchemaCompatCheckMode.CHECK_SCHEMA).get();

        String changedDesc = schemaRepository.getObject("9999").get().getChangeDescription();

        assertEquals("Added new schema which is better", changedDesc);

    }

    @Test
    void testAddSchemaVersion_WithChangeDesc_negative() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.DATA);

        topicRepository.save(topic1).get();

        SchemaMetadata newSchema = new SchemaMetadata();
        newSchema.setId("9999");
        newSchema.setTopicName("topic-1");
        newSchema.setCreatedBy("testuser");
        newSchema.setJsonSchema(buildJsonSchema(List.of("propA", "propB"), List.of("string", "string")));
        newSchema.setSchemaVersion(1);
        newSchema.setChangeDescription("Added new schema which is better");

        try {
            service.addTopicSchemaVersion("test", newSchema, SchemaCompatCheckMode.CHECK_SCHEMA).get();
            fail("Exception expected when adding change description for first published schema");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    void testDeleteSchemaWithSub_positive() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        topicRepository.save(topic1).get();

        SchemaMetadata schema = new SchemaMetadata();
        schema.setId("1234");
        schema.setTopicName("topic-1");
        schema.setCreatedBy("otheruser");
        schema.setJsonSchema(buildJsonSchema(List.of("propA"), List.of("string")));
        schema.setSchemaVersion(1);
        schemaRepository.save(schema).get();

        SubscriptionMetadata subscription = new SubscriptionMetadata();
        subscription.setId("50");
        subscription.setTopicName("topic-1");
        subscription.setClientApplicationId("2");

        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        when(subscriptionService.getSubscriptionsForTopic("test", "topic-1", false)).thenReturn(List.of(subscription));

        ValidatingTopicServiceImpl validatingService = new ValidatingTopicServiceImpl(service, subscriptionService,
                applicationsService, kafkaClusters, topicConfig, true);

        validatingService.deleteLatestTopicSchemaVersion("test", "topic-1").get();

        assertFalse(schemaRepository.getObject("1234").isPresent());
    }

    @Test
    void testDeleteSchemaWithSub_negative() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        topicRepository.save(topic1).get();

        SchemaMetadata schema = new SchemaMetadata();
        schema.setId("1234");
        schema.setTopicName("topic-1");
        schema.setCreatedBy("otheruser");
        schema.setJsonSchema(buildJsonSchema(List.of("propA"), List.of("string")));
        schema.setSchemaVersion(1);
        schemaRepository.save(schema).get();

        SubscriptionMetadata subscription = new SubscriptionMetadata();
        subscription.setId("50");
        subscription.setTopicName("topic-1");
        subscription.setClientApplicationId("2");

        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        when(subscriptionService.getSubscriptionsForTopic("test", "topic-1", false)).thenReturn(List.of(subscription));

        ValidatingTopicServiceImpl validatingService = new ValidatingTopicServiceImpl(service, subscriptionService,
                applicationsService, kafkaClusters, topicConfig, false);

        assertTrue(schemaRepository.getObject("1234").isPresent());

        try {
            validatingService.deleteLatestTopicSchemaVersion("test", "topic-1").get();
            fail("Exception expected when trying to delete schema with subscribers when schemaDeleteWithSub is set to false");
        }
        catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }

    @Test
    void testDeleteLatestSchemaVersionStagedSchemaDeleteSub_negative() throws Exception {
        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        ValidatingTopicServiceImpl validatingService = new ValidatingTopicServiceImpl(service, subscriptionService,
                applicationsService, kafkaClusters, topicConfig, true);

        KafkaCluster prodCluster = mock(KafkaCluster.class);
        when(kafkaClusters.getEnvironment("prod")).thenReturn(Optional.of(prodCluster));
        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("test", "prod"));

        KafkaEnvironmentConfig config = mock(KafkaEnvironmentConfig.class);
        when(config.isStagingOnly()).thenReturn(true);
        when(kafkaClusters.getEnvironmentMetadata("prod")).thenReturn(Optional.of(config));

        TopicBasedRepositoryMock<TopicMetadata> prodTopicRepository = new TopicBasedRepositoryMock<>();
        TopicBasedRepositoryMock<SchemaMetadata> prodSchemaRepository = new TopicBasedRepositoryMock<>();
        when(prodCluster.getRepository("topics", TopicMetadata.class)).thenReturn(prodTopicRepository);
        when(prodCluster.getRepository("schemas", SchemaMetadata.class)).thenReturn(prodSchemaRepository);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        SubscriptionMetadata subscription = new SubscriptionMetadata();
        subscription.setId("50");
        subscription.setTopicName("topic-1");
        subscription.setClientApplicationId("2");

        when(subscriptionService.getSubscriptionsForTopic("prod", "topic-1", false)).thenReturn(List.of(subscription));

        prodTopicRepository.save(topic1).get();

        SchemaMetadata schema = new SchemaMetadata();
        schema.setId("1234");
        schema.setTopicName("topic-1");
        schema.setCreatedBy("otheruser");
        schema.setJsonSchema(buildJsonSchema(List.of("propA"), List.of("string")));
        schema.setSchemaVersion(1);

        prodSchemaRepository.save(schema).get();

        try {
            validatingService.deleteLatestTopicSchemaVersion("prod", "topic-1").get();
            fail("Exception expected, but none thrown");
        }
        catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }

    private static String buildJsonSchema(List<String> propertyNames, List<String> propertyTypes) {
        JSONObject props = new JSONObject();

        for (int i = 0; i < propertyNames.size(); i++) {
            String pn = propertyNames.get(i);
            String tp = propertyTypes.get(i);

            JSONObject prop = new JSONObject();
            prop.put("type", tp);
            props.put(pn, prop);
        }

        JSONObject schema = new JSONObject();
        schema.put("$schema", "someUrl");
        schema.put("properties", props);

        return schema.toString();
    }
}
