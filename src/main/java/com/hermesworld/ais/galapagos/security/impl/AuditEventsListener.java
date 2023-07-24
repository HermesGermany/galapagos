package com.hermesworld.ais.galapagos.security.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.events.*;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.security.GalapagosAuditEventType;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class AuditEventsListener implements TopicEventsListener, SubscriptionEventsListener, ApplicationEventsListener {

    private final AuditEventRepository auditRepository;

    private static final String APPLICATION_ID = "applicationId";

    private static final String ENVIRONMENT_ID = "environmentId";

    private static final String NAME = "name";

    public AuditEventsListener(AuditEventRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Override
    public CompletableFuture<Void> handleTopicCreated(TopicCreatedEvent event) {
        TopicMetadata topic = event.getMetadata();
        TopicCreateParams params = event.getTopicCreateParams();

        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put(NAME, topic.getName());
        auditData.put(ENVIRONMENT_ID, event.getContext().getKafkaCluster().getId());
        auditData.put("description", topic.getDescription());
        auditData.put("topicType", topic.getType());
        auditData.put(APPLICATION_ID, topic.getOwnerApplicationId());
        auditData.put("subscriptionApprovalRequired", topic.isSubscriptionApprovalRequired());
        auditData.put("numPartitions", params.getNumberOfPartitions());
        auditData.put("topicConfig", params.getTopicConfigs());

        auditRepository
                .add(new AuditEvent(getUserName(event), GalapagosAuditEventType.TOPIC_CREATED.name(), auditData));

        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicDeleted(TopicEvent event) {
        TopicMetadata topic = event.getMetadata();

        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put(NAME, topic.getName());
        auditData.put(ENVIRONMENT_ID, event.getContext().getKafkaCluster().getId());

        auditRepository
                .add(new AuditEvent(getUserName(event), GalapagosAuditEventType.TOPIC_DELETED.name(), auditData));

        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicDeprecated(TopicEvent event) {
        return handleTopicEvent(event, "TOPIC_DEPRECATED");
    }

    @Override
    public CompletableFuture<Void> handleTopicDescriptionChanged(TopicEvent event) {
        return handleTopicEvent(event, "TOPIC_DESCRIPTION_CHANGED");
    }

    @Override
    public CompletableFuture<Void> handleTopicUndeprecated(TopicEvent event) {
        return handleTopicEvent(event, "TOPIC_UNDEPRECATED");
    }

    @Override
    public CompletableFuture<Void> handleTopicSchemaAdded(TopicSchemaAddedEvent event) {
        TopicMetadata topic = event.getMetadata();

        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put(NAME, topic.getName());
        auditData.put(ENVIRONMENT_ID, event.getContext().getKafkaCluster().getId());
        auditData.put("schemaVersion", event.getNewSchema().getSchemaVersion());
        auditData.put("jsonSchema", event.getNewSchema().getJsonSchema());

        auditRepository
                .add(new AuditEvent(getUserName(event), GalapagosAuditEventType.TOPIC_SCHEMA_ADDED.name(), auditData));
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicSchemaDeleted(TopicSchemaRemovedEvent event) {
        TopicMetadata topic = event.getMetadata();

        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put(NAME, topic.getName());
        auditData.put(ENVIRONMENT_ID, event.getContext().getKafkaCluster().getId());

        auditRepository
                .add(new AuditEvent(getUserName(event), GalapagosAuditEventType.TOPIC_SCHEMA_ADDED.name(), auditData));
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicSubscriptionApprovalRequiredFlagChanged(TopicEvent event) {
        return handleTopicEvent(event, "TOPIC_SUBSCRIPTION_APPROVAL_REQUIRED_FLAG_CHANGED");
    }

    @Override
    public CompletableFuture<Void> handleAddTopicProducer(TopicAddProducerEvent event) {
        return handleTopicEvent(event, GalapagosAuditEventType.TOPIC_PRODUCER_APPLICATION_ADDED.name());
    }

    @Override
    public CompletableFuture<Void> handleRemoveTopicProducer(TopicRemoveProducerEvent event) {
        return handleTopicEvent(event, GalapagosAuditEventType.TOPIC_PRODUCER_APPLICATION_REMOVED.name());
    }

    @Override
    public CompletableFuture<Void> handleTopicOwnerChanged(TopicOwnerChangeEvent event) {
        return handleTopicEvent(event, GalapagosAuditEventType.TOPIC_OWNER_CHANGED.name());
    }

    @Override
    public CompletableFuture<Void> handleApplicationRegistered(ApplicationEvent event) {
        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put(APPLICATION_ID, event.getMetadata().getApplicationId());
        auditData.put(ENVIRONMENT_ID, event.getContext().getKafkaCluster().getId());
        auditData.put("authentication", event.getMetadata().getAuthenticationJson());

        auditRepository.add(
                new AuditEvent(getUserName(event), GalapagosAuditEventType.APPLICATION_REGISTERED.name(), auditData));
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleApplicationAuthenticationChanged(ApplicationAuthenticationChangeEvent event) {
        return handleApplicationRegistered(event);
    }

    @Override
    public CompletableFuture<Void> handleApplicationOwnerRequestCreated(ApplicationOwnerRequestEvent event) {
        return handleApplicationOwnerRequest(event, GalapagosAuditEventType.APPLICATION_OWNER_REQUESTED, false);
    }

    @Override
    public CompletableFuture<Void> handleApplicationOwnerRequestUpdated(ApplicationOwnerRequestEvent event) {
        return handleApplicationOwnerRequest(event, GalapagosAuditEventType.APPLICATION_OWNER_REQUEST_UPDATED, true);
    }

    @Override
    public CompletableFuture<Void> handleApplicationOwnerRequestCanceled(ApplicationOwnerRequestEvent event) {
        return handleApplicationOwnerRequest(event, GalapagosAuditEventType.APPLICATION_OWNER_REQUEST_CANCELED, false);
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionCreated(SubscriptionEvent event) {
        return handleSubscriptionEvent(event, GalapagosAuditEventType.TOPIC_SUBSCRIBED);
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionDeleted(SubscriptionEvent event) {
        return handleSubscriptionEvent(event, GalapagosAuditEventType.TOPIC_UNSUBSCRIBED);
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionUpdated(SubscriptionEvent event) {
        return handleSubscriptionEvent(event, GalapagosAuditEventType.SUBSCRIPTION_UPDATED);
    }

    private CompletableFuture<Void> handleApplicationOwnerRequest(ApplicationOwnerRequestEvent event,
            GalapagosAuditEventType type, boolean includeStatus) {
        ApplicationOwnerRequest request = event.getRequest();

        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put(APPLICATION_ID, request.getApplicationId());
        auditData.put("requestingUser", request.getUserName());
        if (includeStatus) {
            auditData.put("newStatus", request.getState());
        }

        auditRepository.add(new AuditEvent(getUserName(event), type.name(), auditData));

        return FutureUtil.noop();
    }

    private CompletableFuture<Void> handleTopicEvent(TopicEvent event, String topicEventType) {
        TopicMetadata topic = event.getMetadata();

        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put(NAME, topic.getName());
        auditData.put(ENVIRONMENT_ID, event.getContext().getKafkaCluster().getId());
        auditData.put("description", topic.getDescription());
        auditData.put("topicType", topic.getType());
        auditData.put(APPLICATION_ID, topic.getOwnerApplicationId());
        auditData.put("subscriptionApprovalRequired", topic.isSubscriptionApprovalRequired());
        auditData.put("deprecated", topic.isDeprecated());
        auditData.put("deprecationText", topic.getDeprecationText());
        if (topic.getEolDate() != null) {
            auditData.put("endOfLife", topic.getEolDate().toString());
        }
        if (topic.getProducers() != null) {
            auditData.put("producers", topic.getProducers());
        }
        auditData.put("topicEventType", topicEventType);

        auditRepository
                .add(new AuditEvent(getUserName(event), GalapagosAuditEventType.TOPIC_UPDATED.name(), auditData));

        return FutureUtil.noop();
    }

    private CompletableFuture<Void> handleSubscriptionEvent(SubscriptionEvent event,
            GalapagosAuditEventType eventType) {
        SubscriptionMetadata metadata = event.getMetadata();

        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put(NAME, metadata.getTopicName());
        auditData.put(ENVIRONMENT_ID, event.getContext().getKafkaCluster().getId());
        auditData.put("description", metadata.getDescription());
        auditData.put(APPLICATION_ID, metadata.getClientApplicationId());
        auditData.put("state", metadata.getState() == null ? null : metadata.getState().toString());

        auditRepository.add(new AuditEvent(getUserName(event), eventType.name(), auditData));

        return FutureUtil.noop();
    }

    private String getUserName(AbstractGalapagosEvent event) {
        return event.getContext().getContextValue("username").map(Object::toString).orElse(null);
    }

}
