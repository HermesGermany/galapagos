package com.hermesworld.ais.galapagos.subscriptions.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionState;

public interface SubscriptionService {

    CompletableFuture<SubscriptionMetadata> addSubscription(String environmentId, String topicName,
            String applicationId, String description);

    /**
     * Adds a subscription to a given environment. This method does apply less business logic than
     * {@link #addSubscription(String, String, String, String)}, but still generates a <b>new</b> Subscription Metadata
     * object with a new ID. This method is useful for staging, where some business rules, e.g. regarding protected
     * topics, shall be bypassed. Contrary to the mentioned other method, this method copies the current state of the
     * given metadata object into the new object.
     *
     * @param environmentId ID of the Kafka Environment to create the subscription on.
     * @param subscription  Metadata of an existing subscription (usually from a different environment). Its
     *                      <code>id</code> property is <b>not</b> used when creating the new subscription. Other fields
     *                      are copied.
     * @return A Completable Future which completes when the subscription has been added successfully and returns the
     *         newly created subscription metadata, or which fails in case of any error.
     */
    CompletableFuture<SubscriptionMetadata> addSubscription(String environmentId, SubscriptionMetadata subscription);

    CompletableFuture<Void> deleteSubscription(String environmentId, String subscriptionId);

    List<SubscriptionMetadata> getSubscriptionsForTopic(String environmentId, String topicName,
            boolean includeNonApproved);

    List<SubscriptionMetadata> getSubscriptionsOfApplication(String environmentId, String applicationId,
            boolean includeNonApproved);

    CompletableFuture<Void> updateSubscriptionState(String environmentId, String subscriptionId,
            SubscriptionState newState);

}
