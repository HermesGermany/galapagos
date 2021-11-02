package com.hermesworld.ais.galapagos.events;

import java.util.concurrent.CompletableFuture;

public interface ApplicationEventsListener {

    CompletableFuture<Void> handleApplicationRegistered(ApplicationEvent event);

    CompletableFuture<Void> handleApplicationAuthenticationChanged(ApplicationAuthenticationChangeEvent event);

    CompletableFuture<Void> handleApplicationOwnerRequestCreated(ApplicationOwnerRequestEvent event);

    CompletableFuture<Void> handleApplicationOwnerRequestUpdated(ApplicationOwnerRequestEvent event);

    CompletableFuture<Void> handleApplicationOwnerRequestCanceled(ApplicationOwnerRequestEvent event);
}
