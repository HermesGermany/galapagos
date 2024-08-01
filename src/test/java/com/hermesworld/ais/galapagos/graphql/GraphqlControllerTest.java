package com.hermesworld.ais.galapagos.graphql;

import com.hermesworld.ais.galapagos.GalapagosTestConfig;
import com.hermesworld.ais.galapagos.applications.*;
import com.hermesworld.ais.galapagos.applications.impl.KnownApplicationImpl;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.ValidatingTopicService;
import graphql.GraphQLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(GalapagosTestConfig.class)
class GraphqlControllerTest {

    @SuppressWarnings("unused")
    @MockBean
    private KafkaClusters kafkaClusters;

    @MockBean
    private ValidatingTopicService topicService;

    @MockBean
    private ApplicationsService applicationsService;

    @MockBean
    private SubscriptionService subscriptionService;

    @Autowired
    private GraphqlController graphqlController;

    @Autowired
    ApplicationContext context;

    @Test
    void testTopicsByType() {
        assertNotNull(graphqlController);

        String environmentId = "test-env";
        TopicType topicType = TopicType.EVENTS;

        TopicMetadata topicMetadata = new TopicMetadata();
        topicMetadata.setType(topicType);
        topicMetadata.setName("test-topic");
        topicMetadata.setOwnerApplicationId("owner-app");
        topicMetadata.setProducers(List.of("producer-app"));
        topicMetadata.setEolDate(LocalDate.parse("2024-01-01"));
        topicMetadata.setDeprecationText("Deprecation notice");
        GraphQLContext context = GraphQLContext.newContext().build();

        when(topicService.listTopics(environmentId)).thenReturn(List.of(topicMetadata));
        List<TopicMetadata> topics = graphqlController.topicsByType(environmentId, topicType, context);

        assertNotNull(topics);
        assertEquals(1, topics.size());
        TopicMetadata topic = topics.get(0);
        assertEquals("test-topic", topic.getName());
        assertEquals(TopicType.EVENTS, topic.getType());
        assertEquals("owner-app", topic.getOwnerApplicationId());
        assertNotNull(topic.getProducers());
        assertNotNull(topic.getEolDate());
        assertEquals("Deprecation notice", topic.getDeprecationText());
    }

    @Test
    void testTopicsByTypeWithMultipleTopics() {
        assertNotNull(graphqlController);

        String environmentId = "test-env";
        TopicType topicType = TopicType.EVENTS;

        TopicMetadata topicMetadata1 = new TopicMetadata();
        topicMetadata1.setType(TopicType.EVENTS);
        topicMetadata1.setName("test-topic-1");
        TopicMetadata topicMetadata2 = new TopicMetadata();
        topicMetadata2.setType(TopicType.INTERNAL);
        topicMetadata2.setName("test-topic-2");
        TopicMetadata topicMetadata3 = new TopicMetadata();
        topicMetadata3.setType(TopicType.EVENTS);
        topicMetadata3.setName("test-topic-3");
        GraphQLContext context = GraphQLContext.newContext().build();

        when(topicService.listTopics(environmentId))
                .thenReturn(List.of(topicMetadata1, topicMetadata2, topicMetadata3));
        List<TopicMetadata> topics = graphqlController.topicsByType(environmentId, topicType, context);

        assertNotNull(topics);
        assertEquals(2, topics.size());
        assertEquals("test-topic-1", topics.get(0).getName());
        assertEquals("test-topic-3", topics.get(1).getName());
        assertEquals(TopicType.EVENTS, topics.get(0).getType());
        assertEquals(TopicType.EVENTS, topics.get(1).getType());
    }

    @Test
    void testApplicationsByEnvironmentId() {
        assertNotNull(graphqlController);

        String environmentId = "test-env";
        ApplicationMetadata appMetadata = new ApplicationMetadata();
        appMetadata.setApplicationId("app-id");
        List<ApplicationMetadata> metadataList = List.of(appMetadata);
        KnownApplicationImpl app = new KnownApplicationImpl("app-id", "app-name");
        GraphQLContext context = GraphQLContext.newContext().build();

        when(applicationsService.getAllApplicationMetadata(environmentId)).thenReturn(metadataList);
        when(applicationsService.getKnownApplication("app-id")).thenReturn(Optional.of(app));
        List<KnownApplication> applications = graphqlController.applicationsByEnvironmentId(environmentId, context);

        assertNotNull(applications);
        assertFalse(applications.isEmpty());
        KnownApplication application = applications.get(0);
        assertEquals("app-id", application.getId());
        assertEquals("app-name", application.getName());
    }

    @Test
    void testGetOwnerApplication() {
        assertNotNull(graphqlController);

        TopicMetadata topicMetadata = new TopicMetadata();
        topicMetadata.setOwnerApplicationId("owner-app");
        KnownApplicationImpl app = new KnownApplicationImpl("app-id", "app-name");

        when(applicationsService.getKnownApplication("owner-app")).thenReturn(Optional.of(app));
        Optional<KnownApplication> ownerApplication = graphqlController.getOwnerApplication(topicMetadata);

        assertTrue(ownerApplication.isPresent());
        assertEquals("app-id", ownerApplication.get().getId());
        assertEquals("app-name", ownerApplication.get().getName());
    }

    @Test
    void testGetProducers() {
        assertNotNull(graphqlController);

        TopicMetadata topicMetadata = new TopicMetadata();
        topicMetadata.setProducers(List.of("producer-app"));
        KnownApplicationImpl app = new KnownApplicationImpl("app-id", "app-name");

        when(applicationsService.getKnownApplication("producer-app")).thenReturn(Optional.of(app));
        List<KnownApplication> producers = graphqlController.getProducers(topicMetadata);

        assertNotNull(producers);
        assertEquals(1, producers.size());
        assertEquals("app-id", producers.get(0).getId());
        assertEquals("app-name", producers.get(0).getName());
    }

    @Test
    void testGetSubscriptions() {
        assertNotNull(graphqlController);

        String environmentId = "test-env";
        TopicMetadata topicMetadata = new TopicMetadata();
        topicMetadata.setName("test-topic");
        SubscriptionMetadata subscriptionMetadata = new SubscriptionMetadata();
        subscriptionMetadata.setId("1");
        subscriptionMetadata.setClientApplicationId("client-app");
        subscriptionMetadata.setDescription("Deprecation notice");

        when(subscriptionService.getSubscriptionsForTopic(environmentId, "test-topic", false))
                .thenReturn(List.of(subscriptionMetadata));
        List<SubscriptionMetadata> subscriptions = graphqlController.getSubscriptions(environmentId, topicMetadata);

        assertNotNull(subscriptions);
        assertEquals(1, subscriptions.size());
        assertEquals("1", subscriptions.get(0).getId());
        assertEquals("client-app", subscriptions.get(0).getClientApplicationId());
        assertEquals("Deprecation notice", subscriptions.get(0).getDescription());
    }

    @Test
    void testGetSchemas() {
        assertNotNull(graphqlController);

        String environmentId = "test-env";
        TopicMetadata topicMetadata = new TopicMetadata();
        topicMetadata.setName("test-topic");

        SchemaMetadata schemaMetadata = new SchemaMetadata();
        schemaMetadata.setSchemaVersion(1);
        schemaMetadata.setJsonSchema("{\"json\": \"schema\"}");

        when(topicService.getTopicSchemaVersions(environmentId, "test-topic")).thenReturn(List.of(schemaMetadata));
        List<SchemaMetadata> schemas = graphqlController.getSchemas(environmentId, topicMetadata);

        assertNotNull(schemas);
        assertEquals(1, schemas.size());
        assertEquals(1, schemas.get(0).getSchemaVersion());
        assertEquals("{\"json\": \"schema\"}", schemas.get(0).getJsonSchema());
    }

    @Test
    void testGetClientApplication() {
        assertNotNull(graphqlController);

        SubscriptionMetadata subscriptionMetadata = new SubscriptionMetadata();
        subscriptionMetadata.setClientApplicationId("client-app");
        KnownApplicationImpl app = new KnownApplicationImpl("client-app", "app-name");

        when(applicationsService.getKnownApplication("client-app")).thenReturn(Optional.of(app));
        Optional<KnownApplication> clientApplication = graphqlController.getClientApplication(subscriptionMetadata);

        assertTrue(clientApplication.isPresent());
        assertEquals("client-app", clientApplication.get().getId());
        assertEquals("app-name", clientApplication.get().getName());
    }

    @Test
    void testGetSubscriptionsOfApplication() {
        assertNotNull(graphqlController);

        String environmentId = "test-env";
        KnownApplicationImpl app = new KnownApplicationImpl("app-id", "app-name");
        SubscriptionMetadata subscriptionMetadata = new SubscriptionMetadata();
        subscriptionMetadata.setId("1");
        subscriptionMetadata.setClientApplicationId("client-app");
        subscriptionMetadata.setDescription("Deprecation notice");

        when(subscriptionService.getSubscriptionsOfApplication(environmentId, "app-id", false))
                .thenReturn(List.of(subscriptionMetadata));
        List<SubscriptionMetadata> subscriptions = graphqlController.getSubscriptionsOfApplication(environmentId, app);

        assertNotNull(subscriptions);
        assertEquals(1, subscriptions.size());
        assertEquals("1", subscriptions.get(0).getId());
        assertEquals("client-app", subscriptions.get(0).getClientApplicationId());
        assertEquals("Deprecation notice", subscriptions.get(0).getDescription());
    }

    @Test
    void testGetDevelopers() {
        assertNotNull(graphqlController);

        KnownApplicationImpl app = new KnownApplicationImpl("app-id", "app-name");

        ApplicationOwnerRequest ownerRequest1 = new ApplicationOwnerRequest();
        ownerRequest1.setApplicationId("app-id");
        ownerRequest1.setUserName("developer1");
        ownerRequest1.setState(RequestState.APPROVED);
        ApplicationOwnerRequest ownerRequest2 = new ApplicationOwnerRequest();
        ownerRequest2.setApplicationId("app-id");
        ownerRequest2.setUserName("developer2");
        ownerRequest2.setState(RequestState.APPROVED);
        ApplicationOwnerRequest ownerRequest3 = new ApplicationOwnerRequest();
        ownerRequest3.setApplicationId("app-id");
        ownerRequest3.setUserName("developer3");
        ownerRequest3.setState(RequestState.REJECTED);
        ApplicationOwnerRequest ownerRequest4 = new ApplicationOwnerRequest();
        ownerRequest4.setApplicationId("application-id");
        ownerRequest4.setUserName("developer4");
        ownerRequest4.setState(RequestState.APPROVED);

        when(applicationsService.getAllApplicationOwnerRequests())
                .thenReturn(List.of(ownerRequest1, ownerRequest2, ownerRequest3, ownerRequest4));
        List<String> developers = graphqlController.getDevelopers(app);

        assertNotNull(developers);
        assertEquals(2, developers.size());
        assertEquals("developer1", developers.get(0));
        assertEquals("developer2", developers.get(1));
    }

    @Test
    void testGetAuthenticationInfo() {
        assertNotNull(graphqlController);

        String environmentId = "test-env";
        KnownApplicationImpl app = new KnownApplicationImpl("app-id", "app-name");
        ApplicationMetadata applicationMetadata = new ApplicationMetadata();
        applicationMetadata.setApplicationId("app-id");
        applicationMetadata.setAuthenticationJson("{\"auth\": \"info\"}");

        when(applicationsService.getApplicationMetadata(environmentId, "app-id"))
                .thenReturn(Optional.of(applicationMetadata));
        String authInfo = graphqlController.getAuthenticationInfo(environmentId, app);

        assertNotNull(authInfo);
        assertEquals("{\"auth\": \"info\"}", authInfo);
    }

    @Test
    void testGetAuthenticationInfoWithNull() {
        assertNotNull(graphqlController);

        String environmentId = "test-env";
        KnownApplicationImpl app = new KnownApplicationImpl("app-id", "app-name");
        ApplicationMetadata applicationMetadata = new ApplicationMetadata();
        applicationMetadata.setApplicationId("app-id");
        applicationMetadata.setAuthenticationJson(null);

        when(applicationsService.getApplicationMetadata(environmentId, "app-id"))
                .thenReturn(Optional.of(applicationMetadata));
        String authInfo = graphqlController.getAuthenticationInfo(environmentId, app);

        assertNull(authInfo);
    }

    @Test
    void testGraphQlQuery() {
        String environmentId = "test-env";

        TopicMetadata topicMetadata1 = new TopicMetadata();
        topicMetadata1.setType(TopicType.EVENTS);
        topicMetadata1.setName("test-topic-1");
        TopicMetadata topicMetadata2 = new TopicMetadata();
        topicMetadata2.setType(TopicType.INTERNAL);
        topicMetadata2.setName("test-topic-2");
        TopicMetadata topicMetadata3 = new TopicMetadata();
        topicMetadata3.setType(TopicType.EVENTS);
        topicMetadata3.setName("test-topic-3");

        when(topicService.listTopics(environmentId))
                .thenReturn(List.of(topicMetadata1, topicMetadata2, topicMetadata3));

        ApplicationMetadata appMetadata1 = new ApplicationMetadata();
        appMetadata1.setApplicationId("app-id");
        KnownApplicationImpl app = new KnownApplicationImpl("app-id", "app-name");
        when(applicationsService.getAllApplicationMetadata(environmentId)).thenReturn(List.of(appMetadata1));
        when(applicationsService.getKnownApplication("app-id")).thenReturn(Optional.of(app));

        String query = """
                    query {
                        topicsByType(environmentId: "test-env", topicType: EVENTS) {
                            name
                            type
                            ownerApplication {
                                id
                                name
                            }
                        }
                        applicationsByEnvironmentId(environmentId: "test-env") {
                            id
                            name
                        }
                    }
                """;

        WebTestClient client = MockMvcWebTestClient.bindToApplicationContext((WebApplicationContext) context)
                .configureClient().baseUrl("/graphql").build();
        HttpGraphQlTester graphQlTester = HttpGraphQlTester.create(client);

        graphQlTester.document(query).execute().path("topicsByType").entityList(TopicMetadata.class).hasSize(2);

        graphQlTester.document(query).execute().path("applicationsByEnvironmentId")
                .entityList(KnownApplicationImpl.class).hasSize(1);
    }

    @Test
    void testTopicsByTypeMissingParameters() {
        String queryMissingEnvironmentId = """
                    query {
                        topicsByType(topicType: EVENTS) {
                            name
                            type
                        }
                    }
                """;
        executeInvalidQuery(queryMissingEnvironmentId, "MissingFieldArgument@[topicsByType]");

        String queryMissingTopicType = """
                    query {
                        topicsByType(environmentId: "test-env") {
                            name
                            type
                        }
                    }
                """;
        executeInvalidQuery(queryMissingTopicType, "MissingFieldArgument@[topicsByType]");
    }

    @Test
    void testApplicationsByEnvironmentIdMissingEnvironmentId() {
        String queryMissingEnvironmentIdInApps = """
                    query {
                        applicationsByEnvironmentId {
                            id
                            name
                        }
                    }
                """;

        executeInvalidQuery(queryMissingEnvironmentIdInApps, "MissingFieldArgument@[applicationsByEnvironmentId]");
    }

    private void executeInvalidQuery(String query, String expectedErrorMessage) {
        WebTestClient client = MockMvcWebTestClient.bindToApplicationContext((WebApplicationContext) context)
                .configureClient().baseUrl("/graphql").build();
        HttpGraphQlTester graphQlTester = HttpGraphQlTester.create(client);

        graphQlTester.document(query).execute().errors().satisfy(errors -> {
            assertFalse(errors.isEmpty());
            assertTrue(Objects.requireNonNull(errors.get(0).getMessage()).contains(expectedErrorMessage));
        });
    }
}
