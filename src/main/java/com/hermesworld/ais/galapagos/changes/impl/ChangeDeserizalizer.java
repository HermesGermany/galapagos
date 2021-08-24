package com.hermesworld.ais.galapagos.changes.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.changes.Change;
import com.hermesworld.ais.galapagos.changes.ChangeType;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ChangeDeserizalizer extends StdDeserializer<Change> {

    private static final long serialVersionUID = -2708647785538261028L;

    private static final String TOPIC_NAME = "topicName";

    private static final Change UNKNOWN_CHANGE = new Change() {
        @Override
        public ChangeType getChangeType() {
            return null;
        }
    };

    public ChangeDeserizalizer() {
        super(Change.class);
    }

    @Override
    public Change deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode tree = mapper.readTree(p);

        return deserializeChange(tree, mapper);
    }

    @SuppressWarnings("deprecation")
    private Change deserializeChange(JsonNode tree, ObjectMapper mapper) {
        String ctValue = tree.findValue("changeType").asText();
        ChangeType changeType;
        try {
            changeType = ChangeType.valueOf(ctValue);
        }
        catch (IllegalArgumentException e) {
            log.error("Invalid change type found in Changelog data: " + ctValue);
            // fail gently
            return UNKNOWN_CHANGE;
        }

        try {
            switch (changeType) {
            case TOPIC_CREATED:
                JsonNode params = tree.findValue("createParams");
                if (params == null) {
                    return new V010CreateTopicChange(
                            mapper.treeToValue(tree.findValue("topicMetadata"), TopicMetadata.class));
                }
                return ChangeBase.createTopic(mapper.treeToValue(tree.findValue("topicMetadata"), TopicMetadata.class),
                        mapper.treeToValue(tree.findValue("createParams"), TopicCreateParamsDto.class)
                                .toTopicCreateParams());
            case TOPIC_DELETED:
                return ChangeBase.deleteTopic(tree.findValue(TOPIC_NAME).asText(),
                        tree.findValue("internalTopic").asBoolean());
            case TOPIC_SUBSCRIBED:
                return ChangeBase.subscribeTopic(
                        mapper.treeToValue(tree.findValue("subscriptionMetadata"), SubscriptionMetadata.class));
            case TOPIC_UNSUBSCRIBED:
                return ChangeBase.unsubscribeTopic(
                        mapper.treeToValue(tree.findValue("subscriptionMetadata"), SubscriptionMetadata.class));
            case TOPIC_SUBSCRIPTION_UPDATED:
                return ChangeBase.updateSubscription(
                        mapper.treeToValue(tree.findValue("subscriptionMetadata"), SubscriptionMetadata.class));
            case TOPIC_DESCRIPTION_CHANGED:
                return ChangeBase.updateTopicDescription(tree.findValue(TOPIC_NAME).asText(),
                        tree.findValue("newDescription").asText(), tree.findValue("internalTopic").asBoolean());
            case TOPIC_DEPRECATED:
                return ChangeBase.markTopicDeprecated(tree.findValue(TOPIC_NAME).asText(),
                        tree.findValue("deprecationText").asText(),
                        mapper.treeToValue(tree.findValue("eolDate"), LocalDate.class));
            case TOPIC_UNDEPRECATED:
                return ChangeBase.unmarkTopicDeprecated(tree.findValue(TOPIC_NAME).asText());
            case TOPIC_PRODUCER_APPLICATION_ADDED:
                return ChangeBase.TopicProducerAddChange(tree.findValue(TOPIC_NAME).asText(),
                        tree.findValue("topicProducerIds").asText());
            case TOPIC_PRODUCER_APPLICATION_REMOVED:
                return ChangeBase.TopicProducerRemoveChange(tree.findValue(TOPIC_NAME).asText(),
                        tree.findValue("topicProducerIds").asText());
            case TOPIC_SUBSCRIPTION_APPROVAL_REQUIRED_FLAG_UPDATED:
                return ChangeBase.updateTopicSubscriptionApprovalRequiredFlag(tree.findValue(TOPIC_NAME).asText(),
                        tree.findValue("subscriptionApprovalRequired").asBoolean());
            case TOPIC_CONFIG_UPDATED:
                // we intentionally throw away all additional change information, as it should not be used for anything.
                return new V010UpdateTopicConfig();
            case TOPIC_SCHEMA_VERSION_PUBLISHED:
                return ChangeBase.publishTopicSchemaVersion(tree.findValue(TOPIC_NAME).asText(),
                        mapper.treeToValue(tree.findValue("schemaMetadata"), SchemaMetadata.class));
            case APPLICATION_REGISTERED:
                return ChangeBase.registerApplication(tree.findValue("applicationId").asText(),
                        mapper.treeToValue(tree.findValue("applicationMetadata"), ApplicationMetadata.class));
            case COMPOUND_CHANGE:
                ChangeBase mainChange = (ChangeBase) deserializeChange(tree.findValue("mainChange"), mapper);
                JsonNode changeList = tree.findValue("additionalChanges");
                List<ChangeBase> additionalChanges = new ArrayList<>();
                if (changeList != null && changeList.isArray()) {
                    ArrayNode changeArray = (ArrayNode) changeList;
                    changeArray.forEach(node -> additionalChanges.add((ChangeBase) deserializeChange(node, mapper)));
                }
                return ChangeBase.compoundChange(mainChange, additionalChanges);
            }
        }
        catch (JsonProcessingException e) {
            log.error("Could not read change found in Changelog data", e);
            return UNKNOWN_CHANGE;
        }

        log.error("Unsupported Change type in Changelog data: " + changeType
                + ". KafkaChangeDeserializer class must be updated.");
        return UNKNOWN_CHANGE;

    }

    /**
     * Holder for a CREATE_TOPIC change which was generated from Galapagos 0.1.0 versions and thus did not include the
     * createParams attribute. Can not be used to apply the change to an environment, but can be used for changelog etc.
     *
     * @author AlbrechtFlo
     *
     */
    @JsonSerialize
    static class V010CreateTopicChange implements Change {

        private final TopicMetadata topicMetadata;

        public V010CreateTopicChange(TopicMetadata topicMetadata) {
            this.topicMetadata = topicMetadata;
        }

        public TopicMetadata getTopicMetadata() {
            return topicMetadata;
        }

        @Override
        public ChangeType getChangeType() {
            return ChangeType.TOPIC_CREATED;
        }

    }

    static class V010UpdateTopicConfig implements Change {

        @SuppressWarnings("deprecation")
        @Override
        public ChangeType getChangeType() {
            return ChangeType.TOPIC_CONFIG_UPDATED;
        }

    }
}
