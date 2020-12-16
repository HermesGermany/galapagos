package com.hermesworld.ais.galapagos.events;

import java.util.concurrent.CompletableFuture;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;

public interface GalapagosEventSink {

	CompletableFuture<Void> handleTopicCreated(TopicMetadata metadata, TopicCreateParams topicCreateParams);

	CompletableFuture<Void> handleTopicDeleted(TopicMetadata metadata);

	CompletableFuture<Void> handleTopicDescriptionChanged(TopicMetadata metadata);

	CompletableFuture<Void> handleTopicDeprecated(TopicMetadata metadata);

	CompletableFuture<Void> handleTopicUndeprecated(TopicMetadata metadata);

	CompletableFuture<Void> handleTopicSubscriptionApprovalRequiredFlagChanged(TopicMetadata metadata);

	CompletableFuture<Void> handleTopicSchemaAdded(TopicMetadata metadata, SchemaMetadata newSchema);

	CompletableFuture<Void> handleSubscriptionCreated(SubscriptionMetadata subscription);

	CompletableFuture<Void> handleSubscriptionUpdated(SubscriptionMetadata subscription);

	CompletableFuture<Void> handleSubscriptionDeleted(SubscriptionMetadata subscription);

	CompletableFuture<Void> handleApplicationRegistered(ApplicationMetadata metadata);

	CompletableFuture<Void> handleApplicationCertificateChanged(ApplicationMetadata metadata, String previousDn);

	CompletableFuture<Void> handleApplicationOwnerRequestCreated(ApplicationOwnerRequest request);

	CompletableFuture<Void> handleApplicationOwnerRequestUpdated(ApplicationOwnerRequest request);

	CompletableFuture<Void> handleApplicationOwnerRequestCanceled(ApplicationOwnerRequest request);

}
