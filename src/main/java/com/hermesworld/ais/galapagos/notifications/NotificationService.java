package com.hermesworld.ais.galapagos.notifications;

import java.util.concurrent.CompletableFuture;

import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;

/**
 * Interface of a component which is able to send notification for defined user sets. <br>
 * Galapagos Components should not access the implementation of this interface directly, as all notifications are now
 * sent out by {@link com.hermesworld.ais.galapagos.notifications.impl.NotificationEventListener}, which uses this
 * component.
 */
public interface NotificationService {

    /**
     * Notifies the (active) subscribers of the given topic. Allows to exclude a single user from notification, usually
     * the one performing the change leading to this notification.
     *
     * @param environmentId      ID of the Kafka environment of the topic.
     * @param topicName          Name of the topic to load the subscriptions for.
     * @param notificationParams Content of the notification.
     * @param excludeUser        User name to exclude from notification, or <code>null</code> to not exclude any user.
     * @return A CompletableFuture which completes when the notification e-mail has been sent, or which fails if the
     *         e-mail could not be sent for any reason.
     */
    CompletableFuture<Void> notifySubscribers(String environmentId, String topicName,
            NotificationParams notificationParams, String excludeUser);

    CompletableFuture<Void> notifyRequestor(ApplicationOwnerRequest request, NotificationParams notificationParams);

    CompletableFuture<Void> notifyAdmins(NotificationParams notificationParams);

    CompletableFuture<Void> notifyTopicOwners(String environmentId, String topicName,
            NotificationParams notificationParams);

    CompletableFuture<Void> notifyApplicationTopicOwners(String applicationId, NotificationParams notificationParams);
}
