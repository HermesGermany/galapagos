package com.hermesworld.ais.galapagos.subscriptions.service.impl;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hermesworld.ais.galapagos.events.TopicCreatedEvent;
import com.hermesworld.ais.galapagos.events.TopicEvent;
import com.hermesworld.ais.galapagos.events.TopicEventsListener;
import com.hermesworld.ais.galapagos.events.TopicSchemaAddedEvent;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionState;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.util.FutureUtil;

/**
 * Listener which performs several updates when topic events occur:
 * <ul>
 * <li>When a topic is deleted, all associated subscriptions are deleted. Note that this can only occur for deprecated
 * topics after their "end of life" date; otherwise, the <code>TopicService</code> would prohibit deleting topics with
 * active subscriptions.</li>
 * <li>Updates <code>PENDING</code> subscriptions to {@link SubscriptionState#APPROVED} when the flag
 * <code>subscriptionApprovalRequired</code> of the associated topic is updated.</li>
 * </ul>
 *
 * @author AlbrechtFlo
 *
 */
@Component
public class SubscriptionTopicListener implements TopicEventsListener {

	private SubscriptionService subscriptionService;

	@Autowired
	public SubscriptionTopicListener(SubscriptionService subscriptionService) {
		this.subscriptionService = subscriptionService;
	}

	@Override
	public CompletableFuture<Void> handleTopicCreated(TopicCreatedEvent event) {
		return FutureUtil.noop();
	}

	@Override
	public CompletableFuture<Void> handleTopicDeleted(TopicEvent event) {
		KafkaCluster cluster = event.getContext().getKafkaCluster();
		CompletableFuture<Void> result = FutureUtil.noop();
		for (SubscriptionMetadata subscription : subscriptionService.getSubscriptionsForTopic(cluster.getId(),
				event.getMetadata().getName(), true)) {
			result = result.thenCompose(o -> subscriptionService.deleteSubscription(cluster.getId(), subscription.getId()));
		}
		return result;
	}

	@Override
	public CompletableFuture<Void> handleTopicDescriptionChanged(TopicEvent event) {
		return FutureUtil.noop();
	}

	@Override
	public CompletableFuture<Void> handleTopicDeprecated(TopicEvent event) {
		return FutureUtil.noop();
	}

	@Override
	public CompletableFuture<Void> handleTopicUndeprecated(TopicEvent event) {
		return FutureUtil.noop();
	}

	@Override
	public CompletableFuture<Void> handleTopicSchemaAdded(TopicSchemaAddedEvent event) {
		return FutureUtil.noop();
	}

	@Override
	public CompletableFuture<Void> handleTopicSubscriptionApprovalRequiredFlagChanged(TopicEvent event) {
		KafkaCluster cluster = event.getContext().getKafkaCluster();
		CompletableFuture<Void> result = FutureUtil.noop();

		for (SubscriptionMetadata subscription : subscriptionService.getSubscriptionsForTopic(cluster.getId(),
				event.getMetadata().getName(), true)) {
			if (subscription.getState() == SubscriptionState.PENDING) {
				result = subscriptionService.updateSubscriptionState(cluster.getId(), subscription.getId(), SubscriptionState.APPROVED);
			}
		}
		return result;
	}

}
