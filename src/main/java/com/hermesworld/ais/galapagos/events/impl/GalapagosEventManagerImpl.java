package com.hermesworld.ais.galapagos.events.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.events.*;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import org.json.JSONObject;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Component
public class GalapagosEventManagerImpl implements GalapagosEventManager {

    private final List<TopicEventsListener> topicListeners;

    private final List<SubscriptionEventsListener> subscriptionListeners;

    private final List<ApplicationEventsListener> applicationListeners;

    private final List<EventContextSource> contextSources;

    public GalapagosEventManagerImpl(@Lazy List<TopicEventsListener> topicListeners,
            @Lazy List<SubscriptionEventsListener> subscriptionListeners,
            @Lazy List<ApplicationEventsListener> applicationListeners, @Lazy List<EventContextSource> contextSources) {
        this.topicListeners = topicListeners;
        this.subscriptionListeners = subscriptionListeners;
        this.applicationListeners = applicationListeners;
        this.contextSources = contextSources;
    }

    @Override
    public GalapagosEventSink newEventSink(KafkaCluster kafkaCluster) {
        return new EventSinkImpl(kafkaCluster);
    }

    private class EventSinkImpl implements GalapagosEventSink {

        private final GalapagosEventContext eventContext;

        public EventSinkImpl(KafkaCluster kafkaCluster) {
            this.eventContext = buildEventContext(kafkaCluster);
        }

        @Override
        public CompletableFuture<Void> handleTopicCreated(TopicMetadata metadata, TopicCreateParams topicCreateParams) {
            TopicCreatedEvent event = new TopicCreatedEvent(eventContext, metadata, topicCreateParams);
            return handleEvent(topicListeners, l -> l.handleTopicCreated(event));
        }

        @Override
        public CompletableFuture<Void> handleTopicDeleted(TopicMetadata metadata) {
            TopicEvent event = new TopicEvent(eventContext, metadata);
            return handleEvent(topicListeners, l -> l.handleTopicDeleted(event));
        }

        @Override
        public CompletableFuture<Void> handleTopicDescriptionChanged(TopicMetadata metadata) {
            TopicEvent event = new TopicEvent(eventContext, metadata);
            return handleEvent(topicListeners, l -> l.handleTopicDescriptionChanged(event));
        }

        @Override
        public CompletableFuture<Void> handleTopicDeprecated(TopicMetadata metadata) {
            TopicEvent event = new TopicEvent(eventContext, metadata);
            return handleEvent(topicListeners, l -> l.handleTopicDeprecated(event));
        }

        @Override
        public CompletableFuture<Void> handleTopicUndeprecated(TopicMetadata metadata) {
            TopicEvent event = new TopicEvent(eventContext, metadata);
            return handleEvent(topicListeners, l -> l.handleTopicUndeprecated(event));
        }

        @Override
        public CompletableFuture<Void> handleTopicSubscriptionApprovalRequiredFlagChanged(TopicMetadata metadata) {
            TopicEvent event = new TopicEvent(eventContext, metadata);
            return handleEvent(topicListeners, l -> l.handleTopicSubscriptionApprovalRequiredFlagChanged(event));
        }

        @Override
        public CompletableFuture<Void> handleTopicSchemaAdded(TopicMetadata metadata, SchemaMetadata newSchema) {
            TopicSchemaAddedEvent event = new TopicSchemaAddedEvent(eventContext, metadata, newSchema);
            return handleEvent(topicListeners, l -> l.handleTopicSchemaAdded(event));
        }

        @Override
        public CompletableFuture<Void> handleTopicSchemaDeleted(TopicMetadata metadata) {
            TopicSchemaRemovedEvent event = new TopicSchemaRemovedEvent(eventContext, metadata);
            return handleEvent(topicListeners, l -> l.handleTopicSchemaDeleted(event));
        }

        @Override
        public CompletableFuture<Void> handleSubscriptionCreated(SubscriptionMetadata subscription) {
            SubscriptionEvent event = new SubscriptionEvent(eventContext, subscription);
            return handleEvent(subscriptionListeners, l -> l.handleSubscriptionCreated(event));
        }

        @Override
        public CompletableFuture<Void> handleSubscriptionDeleted(SubscriptionMetadata subscription) {
            SubscriptionEvent event = new SubscriptionEvent(eventContext, subscription);
            return handleEvent(subscriptionListeners, l -> l.handleSubscriptionDeleted(event));
        }

        @Override
        public CompletableFuture<Void> handleSubscriptionUpdated(SubscriptionMetadata subscription) {
            SubscriptionEvent event = new SubscriptionEvent(eventContext, subscription);
            return handleEvent(subscriptionListeners, l -> l.handleSubscriptionUpdated(event));
        }

        @Override
        public CompletableFuture<Void> handleApplicationRegistered(ApplicationMetadata metadata) {
            ApplicationEvent event = new ApplicationEvent(eventContext, metadata);
            return handleEvent(applicationListeners, l -> l.handleApplicationRegistered(event));
        }

        @Override
        public CompletableFuture<Void> handleApplicationAuthenticationChanged(ApplicationMetadata metadata,
                JSONObject oldAuthentication, JSONObject newAuthentication) {
            ApplicationAuthenticationChangeEvent event = new ApplicationAuthenticationChangeEvent(eventContext,
                    metadata, oldAuthentication, newAuthentication);
            return handleEvent(applicationListeners, l -> l.handleApplicationAuthenticationChanged(event));
        }

        @Override
        public CompletableFuture<Void> handleApplicationOwnerRequestCreated(ApplicationOwnerRequest request) {
            ApplicationOwnerRequestEvent event = new ApplicationOwnerRequestEvent(eventContext, request);
            return handleEvent(applicationListeners, l -> l.handleApplicationOwnerRequestCreated(event));
        }

        @Override
        public CompletableFuture<Void> handleApplicationOwnerRequestUpdated(ApplicationOwnerRequest request) {
            ApplicationOwnerRequestEvent event = new ApplicationOwnerRequestEvent(eventContext, request);
            return handleEvent(applicationListeners, l -> l.handleApplicationOwnerRequestUpdated(event));
        }

        @Override
        public CompletableFuture<Void> handleApplicationOwnerRequestCanceled(ApplicationOwnerRequest request) {
            ApplicationOwnerRequestEvent event = new ApplicationOwnerRequestEvent(eventContext, request);
            return handleEvent(applicationListeners, l -> l.handleApplicationOwnerRequestCanceled(event));
        }

        @Override
        public CompletableFuture<Void> handleAddTopicProducer(TopicMetadata metadata, String producerId) {
            TopicAddProducerEvent event = new TopicAddProducerEvent(eventContext, producerId, metadata);
            return handleEvent(topicListeners, l -> l.handleAddTopicProducer(event));
        }

        @Override
        public CompletableFuture<Void> handleRemoveTopicProducer(TopicMetadata metadata, String appDeleteId) {
            TopicRemoveProducerEvent event = new TopicRemoveProducerEvent(eventContext, appDeleteId, metadata);
            return handleEvent(topicListeners, l -> l.handleRemoveTopicProducer(event));
        }

        @Override
        public CompletableFuture<Void> handleTopicOwnerChanged(TopicMetadata metadata,
                String previousOwnerApplicationId) {
            TopicOwnerChangeEvent event = new TopicOwnerChangeEvent(eventContext, previousOwnerApplicationId, metadata);
            return handleEvent(topicListeners, l -> l.handleTopicOwnerChanged(event));
        }

        private <L> CompletableFuture<Void> handleEvent(Collection<L> listeners,
                Function<L, CompletableFuture<Void>> listenerInvocation) {
            CompletableFuture<Void> result = CompletableFuture.completedFuture(null);

            for (L listener : listeners) {
                CompletableFuture<Void> cf = listenerInvocation.apply(listener);
                if (cf != null) {
                    result = result.thenCompose(o -> cf);
                }
            }

            return result;
        }

    }

    private GalapagosEventContext buildEventContext(KafkaCluster kafkaCluster) {
        Map<String, Object> context = new HashMap<>();
        for (EventContextSource contextSource : contextSources) {
            context.putAll(contextSource.getContextValues());
        }

        return new EventContextImpl(kafkaCluster, context);
    }

    private static class EventContextImpl implements GalapagosEventContext {

        private final KafkaCluster kafkaCluster;

        private final Map<String, Object> context;

        public EventContextImpl(KafkaCluster kafkaCluster, Map<String, Object> context) {
            this.kafkaCluster = kafkaCluster;
            this.context = context;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<T> getContextValue(String key) {
            return (Optional<T>) Optional.ofNullable(context.get(key));
        }

        @Override
        public KafkaCluster getKafkaCluster() {
            return kafkaCluster;
        }

    }

}
