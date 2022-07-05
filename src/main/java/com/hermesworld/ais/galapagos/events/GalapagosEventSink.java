package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import org.json.JSONObject;

import java.util.concurrent.CompletableFuture;

public interface GalapagosEventSink {

    CompletableFuture<Void> handleTopicCreated(TopicMetadata metadata, TopicCreateParams topicCreateParams);

    CompletableFuture<Void> handleTopicDeleted(TopicMetadata metadata);

    CompletableFuture<Void> handleTopicDescriptionChanged(TopicMetadata metadata);

    CompletableFuture<Void> handleTopicDeprecated(TopicMetadata metadata);

    CompletableFuture<Void> handleTopicUndeprecated(TopicMetadata metadata);

    CompletableFuture<Void> handleTopicSubscriptionApprovalRequiredFlagChanged(TopicMetadata metadata);

    CompletableFuture<Void> handleTopicSchemaAdded(TopicMetadata metadata, SchemaMetadata newSchema);

    CompletableFuture<Void> handleTopicSchemaDeleted(TopicMetadata metadata);

    CompletableFuture<Void> handleSubscriptionCreated(SubscriptionMetadata subscription);

    CompletableFuture<Void> handleSubscriptionUpdated(SubscriptionMetadata subscription);

    CompletableFuture<Void> handleSubscriptionDeleted(SubscriptionMetadata subscription);

    CompletableFuture<Void> handleApplicationRegistered(ApplicationMetadata metadata);

    CompletableFuture<Void> handleApplicationAuthenticationChanged(ApplicationMetadata metadata,
            JSONObject oldAuthentication, JSONObject newAuthentication);

    CompletableFuture<Void> handleApplicationOwnerRequestCreated(ApplicationOwnerRequest request);

    CompletableFuture<Void> handleApplicationOwnerRequestUpdated(ApplicationOwnerRequest request);

    CompletableFuture<Void> handleApplicationOwnerRequestCanceled(ApplicationOwnerRequest request);

    CompletableFuture<Void> handleAddTopicProducer(TopicMetadata metadata, String producerApplicationId);

    CompletableFuture<Void> handleRemoveTopicProducer(TopicMetadata metadata, String producerApplicationId);

    CompletableFuture<Void> handleTopicOwnerChanged(TopicMetadata metadata, String previousOwnerApplicationId);

}
