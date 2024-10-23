package com.hermesworld.ais.galapagos.events;

import java.util.concurrent.CompletableFuture;

public interface TopicEventsListener {

    CompletableFuture<Void> handleTopicCreated(TopicCreatedEvent event);

    CompletableFuture<Void> handleTopicDeleted(TopicEvent event);

    CompletableFuture<Void> handleTopicDescriptionChanged(TopicEvent event);

    CompletableFuture<Void> handleTopicDeprecated(TopicEvent event);

    CompletableFuture<Void> handleMissingInternalTopicDeleted(TopicEvent event);

    CompletableFuture<Void> handleTopicUndeprecated(TopicEvent event);

    CompletableFuture<Void> handleTopicSchemaAdded(TopicSchemaAddedEvent event);

    CompletableFuture<Void> handleTopicSchemaDeleted(TopicSchemaRemovedEvent event);

    CompletableFuture<Void> handleTopicSubscriptionApprovalRequiredFlagChanged(TopicEvent event);

    CompletableFuture<Void> handleAddTopicProducer(TopicAddProducerEvent event);

    CompletableFuture<Void> handleRemoveTopicProducer(TopicRemoveProducerEvent event);

    CompletableFuture<Void> handleTopicOwnerChanged(TopicOwnerChangeEvent event);

}
