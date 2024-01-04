package com.hermesworld.ais.galapagos.staging.impl;

import com.hermesworld.ais.galapagos.changes.ApplicableChange;
import com.hermesworld.ais.galapagos.changes.Change;
import com.hermesworld.ais.galapagos.changes.ChangeType;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StagingImplTest {

    @Test
    void testSubscriptionIdentity() throws Exception {
        TopicService topicService = mock(TopicService.class);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-2");
        topic1.setType(TopicType.EVENTS);

        when(topicService.listTopics("dev")).thenReturn(List.of(topic1));
        when(topicService.listTopics("int")).thenReturn(List.of(topic1));

        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        SubscriptionMetadata sub1 = new SubscriptionMetadata();
        sub1.setClientApplicationId("app-1");
        sub1.setId("123");
        sub1.setTopicName("topic-1");
        when(subscriptionService.getSubscriptionsOfApplication("dev", "app-1", false)).thenReturn(List.of(sub1));

        StagingImpl staging = StagingImpl.build("app-1", "dev", "int", null, topicService, subscriptionService).get();

        // subscription must be staged
        List<? extends Change> changes = staging.getChanges();
        assertEquals(1, changes.size());
        Change change = changes.get(0);
        assertEquals(ChangeType.TOPIC_SUBSCRIBED, change.getChangeType());

        // now, let's assume subscription exists on INT, then it must not be staged again
        // note that the ID may be different on different environments!
        SubscriptionMetadata sub2 = new SubscriptionMetadata();
        sub2.setClientApplicationId("app-1");
        sub2.setId("456");
        sub2.setTopicName("topic-1");
        when(subscriptionService.getSubscriptionsOfApplication("int", "app-1", false)).thenReturn(List.of(sub2));

        staging = StagingImpl.build("app-1", "dev", "int", null, topicService, subscriptionService).get();
        changes = staging.getChanges();
        assertEquals(0, changes.size());
    }

    @Test
    @DisplayName("should stage new added producer to next stage")
    void testProducerAddStaging() throws Exception {
        TopicService topicService = mock(TopicService.class);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);
        topic1.setProducers(List.of("producer1"));

        TopicMetadata topic2 = new TopicMetadata();
        topic2.setName("topic-1");
        topic2.setOwnerApplicationId("app-1");
        topic2.setType(TopicType.EVENTS);

        when(topicService.listTopics("dev")).thenReturn(List.of(topic1));
        when(topicService.listTopics("int")).thenReturn(List.of(topic2));

        StagingImpl staging = StagingImpl
                .build("app-1", "dev", "int", null, topicService, mock(SubscriptionService.class)).get();

        List<? extends Change> changes = staging.getChanges();
        assertEquals(1, staging.getChanges().size());
        Change change = changes.get(0);
        assertEquals(ChangeType.TOPIC_PRODUCER_APPLICATION_ADDED, change.getChangeType());

        topic2.setProducers(List.of("producer1"));

        staging = StagingImpl.build("app-1", "dev", "int", null, topicService, mock(SubscriptionService.class)).get();
        changes = staging.getChanges();
        assertEquals(0, changes.size());
    }

    @Test
    @DisplayName("should stage removed producer to next stage")
    void testProducerRemoveStaging() throws Exception {
        TopicService topicService = mock(TopicService.class);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);
        topic1.setProducers(List.of("producer1"));

        TopicMetadata topic2 = new TopicMetadata();
        topic2.setName("topic-1");
        topic2.setOwnerApplicationId("app-1");
        topic2.setType(TopicType.EVENTS);
        topic2.setProducers(List.of("producer1"));

        when(topicService.listTopics("dev")).thenReturn(List.of(topic1));
        when(topicService.listTopics("int")).thenReturn(List.of(topic2));

        List<String> producers = new ArrayList<>(topic1.getProducers());
        producers.remove("producer1");
        topic1.setProducers(producers);

        StagingImpl staging = StagingImpl
                .build("app-1", "dev", "int", null, topicService, mock(SubscriptionService.class)).get();

        List<? extends Change> changes = staging.getChanges();
        assertEquals(1, staging.getChanges().size());
        Change change = changes.get(0);
        assertEquals(ChangeType.TOPIC_PRODUCER_APPLICATION_REMOVED, change.getChangeType());
    }

    @Test
    void testSchemaIdentity() throws Exception {
        TopicService topicService = mock(TopicService.class);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        when(topicService.listTopics("dev")).thenReturn(List.of(topic1));
        when(topicService.listTopics("int")).thenReturn(List.of(topic1));

        SchemaMetadata schema1 = new SchemaMetadata();
        schema1.setId("999");
        schema1.setSchemaVersion(1);
        schema1.setCreatedBy("test");
        schema1.setTopicName("topic-1");

        when(topicService.getTopicSchemaVersions("dev", "topic-1")).thenReturn(List.of(schema1));

        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        StagingImpl staging = StagingImpl.build("app-1", "dev", "int", null, topicService, subscriptionService).get();

        // schema must be staged
        List<? extends Change> changes = staging.getChanges();
        assertEquals(1, changes.size());
        Change change = changes.get(0);
        assertEquals(ChangeType.TOPIC_SCHEMA_VERSION_PUBLISHED, change.getChangeType());

        // now, let's assume schema exists on INT, then it must not be staged again
        // note that the ID may be different on different environments!
        SchemaMetadata schema2 = new SchemaMetadata();
        schema2.setId("000");
        schema2.setCreatedBy("test");
        schema2.setTopicName("topic-1");
        schema2.setSchemaVersion(1);
        when(topicService.getTopicSchemaVersions("int", "topic-1")).thenReturn(List.of(schema2));

        staging = StagingImpl.build("app-1", "dev", "int", null, topicService, subscriptionService).get();
        changes = staging.getChanges();
        assertEquals(0, changes.size());
    }

    @Test
    void testCompoundChangeForApiTopicCreation() throws Exception {
        TopicService topicService = mock(TopicService.class);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        when(topicService.listTopics("dev")).thenReturn(List.of(topic1));
        when(topicService.buildTopicCreateParams("dev", "topic-1"))
                .thenReturn(CompletableFuture.completedFuture(new TopicCreateParams(2, 2)));

        SchemaMetadata schema1 = new SchemaMetadata();
        schema1.setId("999");
        schema1.setSchemaVersion(1);
        schema1.setCreatedBy("test");
        schema1.setTopicName("topic-1");

        SchemaMetadata schema2 = new SchemaMetadata();
        schema2.setId("000");
        schema2.setSchemaVersion(2);
        schema2.setCreatedBy("test");
        schema2.setTopicName("topic-1");

        when(topicService.getTopicSchemaVersions("dev", "topic-1")).thenReturn(List.of(schema1, schema2));

        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        StagingImpl staging = StagingImpl.build("app-1", "dev", "int", null, topicService, subscriptionService).get();

        // Must contain TWO changes: One compound change for creating the topic and the first schema, and another one
        // for
        // creating second schema
        List<? extends Change> changes = staging.getChanges();
        assertEquals(2, changes.size());
        assertEquals(ChangeType.COMPOUND_CHANGE, changes.get(0).getChangeType());
        assertEquals(ChangeType.TOPIC_SCHEMA_VERSION_PUBLISHED, changes.get(1).getChangeType());

        String firstChangeJson = JsonUtil.newObjectMapper().writeValueAsString(changes.get(0));
        assertTrue(firstChangeJson.contains("app-1"));
        assertTrue(firstChangeJson.contains("999"));
        assertFalse(firstChangeJson.contains("000"));
    }

    @Test
    void testApiTopicWithoutSchema_fail() throws Exception {
        TopicService topicService = mock(TopicService.class);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);

        when(topicService.listTopics("dev")).thenReturn(List.of(topic1));

        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        StagingImpl staging = StagingImpl.build("app-1", "dev", "int", null, topicService, subscriptionService).get();
        List<? extends Change> changes = staging.getChanges();

        assertEquals(1, changes.size());
        try {
            ((ApplicableChange) changes.get(0)).applyTo(null).get();
            fail("Applying create topic change expected to fail because no JSON schema published");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
            assertTrue(e.getCause().getMessage().contains("schema"));
        }
    }

    @Test
    void testStageDeprecatedTopic_fail() throws Exception {
        TopicService topicService = mock(TopicService.class);

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-1");
        topic1.setType(TopicType.EVENTS);
        topic1.setDeprecated(true);

        when(topicService.listTopics("dev")).thenReturn(List.of(topic1));

        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        StagingImpl staging = StagingImpl.build("app-1", "dev", "int", null, topicService, subscriptionService).get();
        List<? extends Change> changes = staging.getChanges();

        assertEquals(1, changes.size());
        try {
            ((ApplicableChange) changes.get(0)).applyTo(null).get();
            fail("Applying create topic change expected to fail because topic is deprecated, but succeeded.");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
            assertTrue(e.getCause().getMessage().toLowerCase(Locale.US).contains("deprecated"));
        }
    }

}
