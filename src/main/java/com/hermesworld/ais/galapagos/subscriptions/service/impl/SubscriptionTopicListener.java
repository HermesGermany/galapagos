package com.hermesworld.ais.galapagos.subscriptions.service.impl;

import com.hermesworld.ais.galapagos.events.*;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionState;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

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

    private final SubscriptionService subscriptionService;

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
            result = result
                    .thenCompose(o -> subscriptionService.deleteSubscription(cluster.getId(), subscription.getId()));
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
    public CompletableFuture<Void> handleTopicSchemaDeleted(TopicSchemaRemovedEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicSubscriptionApprovalRequiredFlagChanged(TopicEvent event) {
        KafkaCluster cluster = event.getContext().getKafkaCluster();
        CompletableFuture<Void> result = FutureUtil.noop();

        for (SubscriptionMetadata subscription : subscriptionService.getSubscriptionsForTopic(cluster.getId(),
                event.getMetadata().getName(), true)) {
            if (subscription.getState() == SubscriptionState.PENDING) {
                result = subscriptionService.updateSubscriptionState(cluster.getId(), subscription.getId(),
                        SubscriptionState.APPROVED);
            }
        }
        return result;
    }

    @Override
    public CompletableFuture<Void> handleAddTopicProducer(TopicAddProducerEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleRemoveTopicProducer(TopicRemoveProducerEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicOwnerChanged(TopicOwnerChangeEvent event) {
        return FutureUtil.noop();
    }

}
