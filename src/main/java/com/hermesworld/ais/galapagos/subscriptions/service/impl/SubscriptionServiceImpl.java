package com.hermesworld.ais.galapagos.subscriptions.service.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.events.GalapagosEventManager;
import com.hermesworld.ais.galapagos.events.GalapagosEventSink;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.InitPerCluster;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionState;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
public class SubscriptionServiceImpl implements SubscriptionService, InitPerCluster {

    private final KafkaClusters kafkaEnvironments;

    private final ApplicationsService applicationsService;

    private final TopicService topicService;

    private final GalapagosEventManager eventManager;

    private static final String TOPIC_NAME = "subscriptions";

    public SubscriptionServiceImpl(KafkaClusters kafkaEnvironments, ApplicationsService applicationsService,
            @Qualifier(value = "nonvalidating") TopicService topicService, GalapagosEventManager eventManager) {
        this.kafkaEnvironments = kafkaEnvironments;
        this.applicationsService = applicationsService;
        this.topicService = topicService;
        this.eventManager = eventManager;
    }

    @Override
    public void init(KafkaCluster cluster) {
        getRepository(cluster).getObjects();
    }

    @Override
    public CompletableFuture<SubscriptionMetadata> addSubscription(String environmentId,
            SubscriptionMetadata subscriptionMetadata) {
        KafkaCluster kafkaCluster = kafkaEnvironments.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return noSuchEnvironment(environmentId);
        }

        TopicMetadata topic = topicService.getTopic(environmentId, subscriptionMetadata.getTopicName()).orElse(null);
        if (topic == null) {
            return noSuchTopic(environmentId, subscriptionMetadata.getTopicName());
        }

        ApplicationMetadata application = applicationsService
                .getApplicationMetadata(environmentId, subscriptionMetadata.getClientApplicationId()).orElse(null);
        if (application == null) {
            return CompletableFuture.failedFuture(new NoSuchElementException(
                    "Application not registered on this environment. Please create authentication data first."));
        }

        if (topic.getType() == TopicType.INTERNAL) {
            return CompletableFuture
                    .failedFuture(new IllegalArgumentException("Cannot subscribe to application internal topics"));
        }

        List<SubscriptionMetadata> subscriptionsForTopic = getSubscriptionsForTopic(environmentId,
                subscriptionMetadata.getTopicName(), true);
        for (var subscription : subscriptionsForTopic) {
            if (Objects.equals(subscription.getClientApplicationId(), subscriptionMetadata.getClientApplicationId())) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "A subscription of this topic for this application already exists."));
            }
        }

        SubscriptionMetadata subscription = new SubscriptionMetadata();
        subscription.setId(UUID.randomUUID().toString());
        subscription.setTopicName(subscriptionMetadata.getTopicName());
        subscription.setClientApplicationId(subscriptionMetadata.getClientApplicationId());
        subscription.setState(subscriptionMetadata.getState());
        subscription.setDescription(subscriptionMetadata.getDescription());

        GalapagosEventSink eventSink = eventManager.newEventSink(kafkaCluster);

        return getRepository(kafkaCluster).save(subscription)
                .thenCompose(o -> eventSink.handleSubscriptionCreated(subscription)).thenApply(o -> subscription);
    }

    @Override
    public CompletableFuture<SubscriptionMetadata> addSubscription(String environmentId, String topicName,
            String applicationId, String description) {
        KafkaCluster kafkaCluster = kafkaEnvironments.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return noSuchEnvironment(environmentId);
        }

        TopicMetadata topic = topicService.getTopic(environmentId, topicName).orElse(null);
        if (topic == null) {
            return noSuchTopic(environmentId, topicName);
        }

        SubscriptionMetadata subscription = new SubscriptionMetadata();
        subscription.setId(UUID.randomUUID().toString());
        subscription.setTopicName(topicName);
        subscription.setClientApplicationId(applicationId);
        subscription.setState(
                topic.isSubscriptionApprovalRequired() ? SubscriptionState.PENDING : SubscriptionState.APPROVED);
        subscription.setDescription(description);

        return addSubscription(environmentId, subscription);
    }

    @Override
    public CompletableFuture<Void> updateSubscriptionState(String environmentId, String subscriptionId,
            SubscriptionState newState) {
        return doWithClusterAndSubscription(environmentId, subscriptionId, (kafkaCluster, subscription) -> {
            SubscriptionState state = newState;
            if (state.equals(subscription.getState())) {
                return FutureUtil.noop();
            }

            // REJECTED is only possible for PENDING subscriptions. Change to CANCELED automatically for APPROVEDs.
            if (state == SubscriptionState.REJECTED && subscription.getState() == SubscriptionState.APPROVED) {
                state = SubscriptionState.CANCELED;
            }

            subscription.setState(state);

            GalapagosEventSink eventSink = eventManager.newEventSink(kafkaCluster);

            // REJECTED and CANCELED subscriptions will instantly be deleted, to allow re-submission
            return (state == SubscriptionState.REJECTED || state == SubscriptionState.CANCELED
                    ? getRepository(kafkaCluster).delete(subscription)
                    : getRepository(kafkaCluster).save(subscription))
                    .thenCompose(o -> eventSink.handleSubscriptionUpdated(subscription));
        });
    }

    @Override
    public CompletableFuture<Void> deleteSubscription(String environmentId, String subscriptionId) {
        return doWithClusterAndSubscription(environmentId, subscriptionId, (kafkaCluster, subscription) -> {
            GalapagosEventSink eventSink = eventManager.newEventSink(kafkaCluster);
            return getRepository(kafkaCluster).delete(subscription)
                    .thenCompose(o -> eventSink.handleSubscriptionDeleted(subscription));
        });
    }

    @Override
    public List<SubscriptionMetadata> getSubscriptionsForTopic(String environmentId, String topicName,
            boolean includeNonApproved) {
        Predicate<SubscriptionMetadata> inclusionFilter = inclusionFilter(includeNonApproved);

        return kafkaEnvironments.getEnvironment(environmentId)
                .map(cluster -> getRepository(cluster).getObjects().stream()
                        .filter(s -> topicName.equals(s.getTopicName())).filter(inclusionFilter)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @Override
    public List<SubscriptionMetadata> getSubscriptionsOfApplication(String environmentId, String applicationId,
            boolean includeNonApproved) {
        Predicate<SubscriptionMetadata> inclusionFilter = inclusionFilter(includeNonApproved);
        return kafkaEnvironments.getEnvironment(environmentId)
                .map(cluster -> getRepository(cluster).getObjects().stream()
                        .filter(s -> applicationId.equals(s.getClientApplicationId())).filter(inclusionFilter)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    private TopicBasedRepository<SubscriptionMetadata> getRepository(KafkaCluster kafkaCluster) {
        return kafkaCluster.getRepository(TOPIC_NAME, SubscriptionMetadata.class);
    }

    private <T> CompletableFuture<T> doWithClusterAndSubscription(String environmentId, String subscriptionId,
            BiFunction<KafkaCluster, SubscriptionMetadata, CompletableFuture<T>> task) {
        KafkaCluster kafkaCluster = kafkaEnvironments.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return noSuchEnvironment(environmentId);
        }

        SubscriptionMetadata subscription = getRepository(kafkaCluster).getObject(subscriptionId).orElse(null);
        if (subscription == null) {
            return CompletableFuture.failedFuture(new NoSuchElementException());
        }

        return task.apply(kafkaCluster, subscription);
    }

    private static <T> CompletableFuture<T> noSuchEnvironment(String environmentId) {
        return CompletableFuture
                .failedFuture(new NoSuchElementException("No environment with ID " + environmentId + " found."));
    }

    private static <T> CompletableFuture<T> noSuchTopic(String environmentId, String topicName) {
        return CompletableFuture.failedFuture(new NoSuchElementException(
                "No topic with name " + topicName + " found on environment " + environmentId + "."));
    }

    private static Predicate<SubscriptionMetadata> inclusionFilter(boolean includeNonApproved) {
        return includeNonApproved ? s -> true : s -> s.getState() == SubscriptionState.APPROVED;
    }

}
