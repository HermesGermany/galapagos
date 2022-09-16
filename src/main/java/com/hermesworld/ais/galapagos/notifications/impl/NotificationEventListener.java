package com.hermesworld.ais.galapagos.notifications.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.events.*;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.notifications.NotificationParams;
import com.hermesworld.ais.galapagos.notifications.NotificationService;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionState;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * This is the central component listening for all types of events in Galapagos and notifying the relevant parties. Here
 * is the mapping logic of "what happened?" to "who should be notified?". For the real notification, the
 * {@link NotificationService} is used.
 *
 * @author AlbrechtFlo
 */
@Component
@Slf4j
public class NotificationEventListener
        implements TopicEventsListener, SubscriptionEventsListener, ApplicationEventsListener, EventContextSource {

    private final NotificationService notificationService;

    private final ApplicationsService applicationsService;

    private final TopicService topicService;

    private final CurrentUserService userService;

    private final KafkaClusters kafkaClusters;

    // TODO externalize
    private final String unknownApp = "(unknown app)";
    private final String unknownUser = "(unknown user)";
    private final String unknownEnv = "(unknown environment)";

    private static final String HTTP_REQUEST_URL_KEY = NotificationEventListener.class.getName() + "_requestUrl";

    private static final String USER_NAME_KEY = NotificationEventListener.class.getName() + "_userName";

    private static final String IS_ADMIN_KEY = NotificationEventListener.class.getName() + "_isAdmin";

    @Autowired
    public NotificationEventListener(NotificationService notificationService, ApplicationsService applicationsService,
            TopicService topicService, CurrentUserService userService, KafkaClusters kafkaClusters) {
        this.notificationService = notificationService;
        this.applicationsService = applicationsService;
        this.topicService = topicService;
        this.userService = userService;
        this.kafkaClusters = kafkaClusters;
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionCreated(SubscriptionEvent event) {
        if (event.getMetadata().getState() == SubscriptionState.PENDING) {
            String topicName = event.getMetadata().getTopicName();
            String environmentId = event.getContext().getKafkaCluster().getId();

            String clientApplicationName = applicationsService
                    .getKnownApplication(event.getMetadata().getClientApplicationId()).map(KnownApplication::getName)
                    .orElse(unknownApp);
            String ownerApplicationName = topicService.getTopic(environmentId, topicName)
                    .flatMap(t -> applicationsService.getKnownApplication(t.getOwnerApplicationId()))
                    .map(KnownApplication::getName).orElse(unknownApp);

            NotificationParams params = new NotificationParams("approve_subscription");
            params.addVariable("topic_name", topicName);
            params.addVariable("client_application_name", clientApplicationName);
            params.addVariable("owner_application_name", ownerApplicationName);
            params.addVariable("subscription_description", event.getMetadata().getDescription());
            params.addVariable("galapagos_topic_url",
                    buildUIUrl(event, "/topics/" + topicName + "?environment=" + environmentId));
            return notificationService.notifyTopicOwners(environmentId, topicName, params);
        }

        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionDeleted(SubscriptionEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionUpdated(SubscriptionEvent event) {
        String topicName = event.getMetadata().getTopicName();
        String environmentId = event.getContext().getKafkaCluster().getId();
        String environmentName = kafkaClusters.getEnvironmentMetadata(environmentId)
                .map(KafkaEnvironmentConfig::getName).orElse(unknownEnv);

        String clientApplicationId = event.getMetadata().getClientApplicationId();
        String clientApplicationName = applicationsService
                .getKnownApplication(event.getMetadata().getClientApplicationId()).map(KnownApplication::getName)
                .orElse(unknownApp);
        String ownerApplicationName = topicService.getTopic(environmentId, topicName)
                .flatMap(t -> applicationsService.getKnownApplication(t.getOwnerApplicationId()))
                .map(KnownApplication::getName).orElse(unknownApp);

        NotificationParams params = new NotificationParams(
                "subscription-" + event.getMetadata().getState().name().toLowerCase(Locale.US));
        params.addVariable("topic_name", topicName);
        params.addVariable("env_name", environmentName);
        params.addVariable("client_application_name", clientApplicationName);
        params.addVariable("owner_application_name", ownerApplicationName);
        params.addVariable("galapagos_topic_url",
                buildUIUrl(event, "/topics/" + topicName + "?environment=" + environmentId));

        return notificationService.notifyApplicationTopicOwners(clientApplicationId, params);
    }

    @Override
    public CompletableFuture<Void> handleTopicCreated(TopicCreatedEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicDeleted(TopicEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicDescriptionChanged(TopicEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicDeprecated(TopicEvent event) {
        // only notify for production environment, to avoid N mails for N environments
        if (kafkaClusters.getProductionEnvironmentId().equals(event.getContext().getKafkaCluster().getId())) {
            return handleTopicChange(event, "als \"deprecated\" markiert");
        }
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicUndeprecated(TopicEvent event) {
        // only notify for production environment, to avoid N mails for N environments
        if (kafkaClusters.getProductionEnvironmentId().equals(event.getContext().getKafkaCluster().getId())) {
            return handleTopicChange(event, "die \"deprecated\"-Markierung entfernt");
        }
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicSchemaAdded(TopicSchemaAddedEvent event) {
        return handleTopicChange(event,
                "ein neues JSON-Schema veröffentlicht (" + event.getNewSchema().getChangeDescription() + ")");
    }

    @Override
    public CompletableFuture<Void> handleTopicSchemaDeleted(TopicSchemaRemovedEvent event) {
        return handleTopicChange(event, "ein JSON-Schema wurde gelöscht ( )");
    }

    @Override
    public CompletableFuture<Void> handleTopicSubscriptionApprovalRequiredFlagChanged(TopicEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleAddTopicProducer(TopicAddProducerEvent event) {
        NotificationParams params = new NotificationParams("new-producer-added");
        String currentUserEmail = userService.getCurrentUserEmailAddress().orElse(unknownUser);
        params.addVariable("topicName", event.getMetadata().getName());
        params.addVariable("environmentId", event.getContext().getKafkaCluster().getId().toUpperCase());
        Optional<KnownApplication> producerApp = applicationsService
                .getKnownApplication(event.getProducerApplicationId());
        Optional<KnownApplication> ownerApp = applicationsService
                .getKnownApplication(event.getMetadata().getOwnerApplicationId());
        params.addVariable("producerApplicationId", producerApp.map(KnownApplication::getName).orElse(unknownApp));
        params.addVariable("topicOwner", ownerApp.map(KnownApplication::getName).orElse(unknownApp));
        params.addVariable("galapagos_apps_url", buildUIUrl(event, "/applications"));

        return notificationService.notifyProducer(params, currentUserEmail, event.getProducerApplicationId());
    }

    @Override
    public CompletableFuture<Void> handleRemoveTopicProducer(TopicRemoveProducerEvent event) {
        NotificationParams params = new NotificationParams("producer-deleted");
        String currentUserEmail = userService.getCurrentUserEmailAddress().orElse(unknownUser);
        params.addVariable("topicName", event.getMetadata().getName());
        params.addVariable("environmentId", event.getContext().getKafkaCluster().getId().toUpperCase());
        Optional<KnownApplication> producerApp = applicationsService
                .getKnownApplication(event.getProducerApplicationId());
        Optional<KnownApplication> ownerApp = applicationsService
                .getKnownApplication(event.getMetadata().getOwnerApplicationId());
        params.addVariable("producerApplicationId", producerApp.map(KnownApplication::getName).orElse(unknownApp));
        params.addVariable("topicOwner", ownerApp.map(KnownApplication::getName).orElse(unknownApp));
        params.addVariable("galapagos_apps_url", buildUIUrl(event, "/applications"));

        return notificationService.notifyProducer(params, currentUserEmail, event.getProducerApplicationId());
    }

    @Override
    public CompletableFuture<Void> handleTopicOwnerChanged(TopicOwnerChangeEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleApplicationRegistered(ApplicationEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleApplicationAuthenticationChanged(ApplicationAuthenticationChangeEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleApplicationOwnerRequestCreated(ApplicationOwnerRequestEvent event) {
        NotificationParams params = new NotificationParams("new-appowner-request");
        params.addVariable("galapagos_admin_url", buildUIUrl(event, "/admin"));

        Optional<Boolean> isAdmin = event.getContext().getContextValue(IS_ADMIN_KEY).map(o -> (Boolean) o);
        if (isAdmin.orElse(false)) {
            return FutureUtil.noop();
        }
        return notificationService.notifyAdmins(params);
    }

    @Override
    public CompletableFuture<Void> handleApplicationOwnerRequestUpdated(ApplicationOwnerRequestEvent event) {
        RequestState newState = event.getRequest().getState();
        String userName = event.getContext().getContextValue(USER_NAME_KEY).map(Object::toString).orElse(unknownUser);

        String requestorUserName = event.getRequest().getUserName();
        if (userName.equals(requestorUserName)) {
            return FutureUtil.noop();
        }

        NotificationParams params = new NotificationParams(
                "appowner-request-" + newState.toString().toLowerCase(Locale.US));
        params.addVariable("galapagos_apps_url", buildUIUrl(event, "/applications"));
        params.addVariable("user_name", requestorUserName);
        params.addVariable("updated_by", userName);
        Optional<KnownApplication> app = applicationsService.getKnownApplication(event.getRequest().getApplicationId());
        params.addVariable("app_name", app.map(KnownApplication::getName).orElse(unknownApp));

        return notificationService.notifyRequestor(event.getRequest(), params);
    }

    @Override
    public CompletableFuture<Void> handleApplicationOwnerRequestCanceled(ApplicationOwnerRequestEvent event) {
        return FutureUtil.noop();
    }

    private CompletableFuture<Void> handleTopicChange(TopicEvent event, String changeText) {
        String environmentId = event.getContext().getKafkaCluster().getId();
        String topicName = event.getMetadata().getName();

        String userName = event.getContext().getContextValue(USER_NAME_KEY).map(Object::toString).orElse(unknownUser);
        String environmentName = kafkaClusters.getEnvironmentMetadata(environmentId)
                .map(KafkaEnvironmentConfig::getName).orElse(unknownEnv);

        // TODO externalize strings
        NotificationParams params = new NotificationParams("topic-changed");
        params.addVariable("user_name", userName);
        params.addVariable("topic_name", topicName);
        params.addVariable("change_action_text", changeText);
        params.addVariable("galapagos_topic_url",
                buildUIUrl(event, "/topics/" + topicName + "?environment=" + environmentId));
        params.addVariable("environment_name", environmentName);
        return notificationService.notifySubscribers(environmentId, topicName, params, userName);
    }

    @Override
    public Map<String, Object> getContextValues() {
        // store the HttpRequest in the event context, as we may otherwise not be able to get it later (different
        // Thread)
        // same for current user name

        Map<String, Object> result = new HashMap<>();
        getCurrentHttpRequest().ifPresent(req -> result.put(HTTP_REQUEST_URL_KEY, req.getRequestURL().toString()));
        userService.getCurrentUserName().ifPresent(name -> result.put(USER_NAME_KEY, name));
        result.put(IS_ADMIN_KEY, userService.isAdmin());

        return result;
    }

    private String buildUIUrl(AbstractGalapagosEvent event, String uri) {
        Optional<String> opRequestUrl = event.getContext().getContextValue(HTTP_REQUEST_URL_KEY);
        if (opRequestUrl.isEmpty()) {
            return "#";
        }

        try {
            URL requestUrl = new URL(opRequestUrl.get());
            return new URL(requestUrl.getProtocol(), requestUrl.getHost(), requestUrl.getPort(),
                    "/app/" + (uri.startsWith("/") ? uri.substring(1) : uri)).toString();
        }
        catch (MalformedURLException e) {
            log.warn("Could not parse request URL from HTTP Request", e);
            return "#";
        }
    }

    private static Optional<HttpServletRequest> getCurrentHttpRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes()).filter(
                requestAttributes -> ServletRequestAttributes.class.isAssignableFrom(requestAttributes.getClass()))
                .map(requestAttributes -> ((ServletRequestAttributes) requestAttributes))
                .map(ServletRequestAttributes::getRequest);
    }

}

