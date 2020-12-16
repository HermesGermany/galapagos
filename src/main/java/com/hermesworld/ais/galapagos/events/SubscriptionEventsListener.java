package com.hermesworld.ais.galapagos.events;

import java.util.concurrent.CompletableFuture;

public interface SubscriptionEventsListener {

	CompletableFuture<Void> handleSubscriptionCreated(SubscriptionEvent event);

	CompletableFuture<Void> handleSubscriptionDeleted(SubscriptionEvent event);

	CompletableFuture<Void> handleSubscriptionUpdated(SubscriptionEvent event);

}
