package com.hermesworld.ais.galapagos.changes.impl;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.hermesworld.ais.galapagos.changes.ChangeData;
import com.hermesworld.ais.galapagos.changes.ChangeType;
import com.hermesworld.ais.galapagos.events.GalapagosEventContext;
import com.hermesworld.ais.galapagos.events.TopicCreatedEvent;
import com.hermesworld.ais.galapagos.events.TopicEvent;
import com.hermesworld.ais.galapagos.events.TopicSchemaAddedEvent;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.security.AuditPrincipal;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.util.HasKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChangesServiceImplTest {

    private GalapagosEventContext context;

    private ChangesServiceImpl impl;

    private AuditPrincipal principal;

    @BeforeEach
    void buildMocks() {
        KafkaClusters clusters = mock(KafkaClusters.class);
        impl = new ChangesServiceImpl(clusters);

        MockCluster cluster = new MockCluster("_test");
        when(clusters.getEnvironment("_test")).thenReturn(Optional.of(cluster.getCluster()));
        principal = new AuditPrincipal("testuser", "Test User");

        context = mock(GalapagosEventContext.class);
        when(context.getKafkaCluster()).thenReturn(cluster.getCluster());
        when(context.getContextValue("principal")).thenReturn(Optional.of(principal));
    }

    @Test
    void testChangeListener_createTopic() {
        TopicMetadata metadata = buildTopicMetadata();
        TopicCreateParams params = new TopicCreateParams(2, 2);

        TopicCreatedEvent event = new TopicCreatedEvent(context, metadata, params);
        impl.handleTopicCreated(event);

        List<ChangeData> log = impl.getChangeLog("_test");
        assertEquals(1, log.size());

        ChangeData data = log.get(0);
        assertEquals(principal.getFullName(), data.getPrincipalFullName());
        assertEquals(ChangeType.TOPIC_CREATED, data.getChange().getChangeType());
    }

    @Test
    void testChangeListener_deleteTopic() {
        TopicMetadata metadata = buildTopicMetadata();
        TopicEvent event = new TopicEvent(context, metadata);
        impl.handleTopicDeleted(event);

        List<ChangeData> log = impl.getChangeLog("_test");
        assertEquals(1, log.size());

        ChangeData data = log.get(0);
        assertEquals(principal.getFullName(), data.getPrincipalFullName());
        assertEquals(ChangeType.TOPIC_DELETED, data.getChange().getChangeType());
    }

    @Test
    void testChangeListener_topicDescriptionChanged() {
        TopicMetadata metadata = buildTopicMetadata();
        TopicEvent event = new TopicEvent(context, metadata);
        impl.handleTopicDescriptionChanged(event);

        List<ChangeData> log = impl.getChangeLog("_test");
        assertEquals(1, log.size());

        ChangeData data = log.get(0);
        assertEquals(principal.getFullName(), data.getPrincipalFullName());
        assertEquals(ChangeType.TOPIC_DESCRIPTION_CHANGED, data.getChange().getChangeType());
    }

    @Test
    void testChangeListener_topicDeprecated() {
        TopicMetadata metadata = buildTopicMetadata();
        metadata.setDeprecated(true);
        metadata.setDeprecationText("do not use");
        metadata.setEolDate(LocalDate.of(2099, 1, 1));

        TopicEvent event = new TopicEvent(context, metadata);
        impl.handleTopicDeprecated(event);

        List<ChangeData> log = impl.getChangeLog("_test");
        assertEquals(1, log.size());

        ChangeData data = log.get(0);
        assertEquals(principal.getFullName(), data.getPrincipalFullName());
        assertEquals(ChangeType.TOPIC_DEPRECATED, data.getChange().getChangeType());
    }

    @Test
    void testChangeListener_topicUndeprecated() {
        TopicMetadata metadata = buildTopicMetadata();
        TopicEvent event = new TopicEvent(context, metadata);
        impl.handleTopicUndeprecated(event);

        List<ChangeData> log = impl.getChangeLog("_test");
        assertEquals(1, log.size());

        ChangeData data = log.get(0);
        assertEquals(principal.getFullName(), data.getPrincipalFullName());
        assertEquals(ChangeType.TOPIC_UNDEPRECATED, data.getChange().getChangeType());
    }

    @Test
    void testChangeListener_topicSchemaVersionPublished() {
        TopicMetadata metadata = buildTopicMetadata();
        SchemaMetadata schema = new SchemaMetadata();
        schema.setId("99");
        schema.setCreatedBy("testuser");
        schema.setCreatedAt(ZonedDateTime.now());
        schema.setJsonSchema("{}");
        schema.setSchemaVersion(1);
        schema.setTopicName("testtopic");

        TopicSchemaAddedEvent event = new TopicSchemaAddedEvent(context, metadata, schema);
        impl.handleTopicSchemaAdded(event);

        List<ChangeData> log = impl.getChangeLog("_test");
        assertEquals(1, log.size());

        ChangeData data = log.get(0);
        assertEquals(principal.getFullName(), data.getPrincipalFullName());
        assertEquals(ChangeType.TOPIC_SCHEMA_VERSION_PUBLISHED, data.getChange().getChangeType());
    }

    private TopicMetadata buildTopicMetadata() {
        TopicMetadata metadata = new TopicMetadata();
        metadata.setName("testtopic");
        metadata.setDescription("Testtopic description");
        metadata.setOwnerApplicationId("123");
        metadata.setType(TopicType.EVENTS);
        return metadata;
    }

    private static class MockCluster {

        private KafkaCluster cluster;

        private Map<String, TopicBasedRepositoryMock<?>> repositories = new HashMap<>();

        public MockCluster(String id) {
            cluster = mock(KafkaCluster.class);
            when(cluster.getId()).thenReturn(id);
            when(cluster.getRepository(any(), any())).then(inv -> repositories.computeIfAbsent(inv.getArgument(0),
                    key -> new TopicBasedRepositoryMock<HasKey>()));
        }

        public KafkaCluster getCluster() {
            return cluster;
        }
    }

}
