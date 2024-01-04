package com.hermesworld.ais.galapagos.changes.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermesworld.ais.galapagos.changes.Change;
import com.hermesworld.ais.galapagos.changes.ChangeType;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangeDeserializerTest {

    @Test
    void testCompoundChangeDeser() throws Exception {
        // ChangesDeserializer is registered in the ObjectMapper by this method
        ObjectMapper mapper = JsonUtil.newObjectMapper();

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("123");
        topic.setType(TopicType.EVENTS);
        ChangeBase change1 = ChangeBase.createTopic(topic, new TopicCreateParams(2, 1));

        SchemaMetadata schema1 = new SchemaMetadata();
        schema1.setId("999");
        schema1.setCreatedAt(ZonedDateTime.of(2020, 5, 26, 16, 19, 10, 0, ZoneOffset.UTC));
        schema1.setCreatedBy("testuser");
        schema1.setJsonSchema("{ }");
        schema1.setSchemaVersion(1);

        ChangeBase change2 = ChangeBase.publishTopicSchemaVersion("topic-1", schema1);

        ChangeBase compound = ChangeBase.compoundChange(change1, List.of(change2));
        String json = mapper.writeValueAsString(compound);

        ChangeBase deser = (ChangeBase) mapper.readValue(json, Change.class);

        assertEquals(ChangeType.COMPOUND_CHANGE, deser.getChangeType());
    }

}
