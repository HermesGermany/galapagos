package com.hermesworld.ais.galapagos.topics.service.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicInUseException;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.config.GalapagosTopicConfig;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import com.hermesworld.ais.galapagos.topics.service.ValidatingTopicService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Primary
public class ValidatingTopicServiceImpl implements ValidatingTopicService {

    private final TopicService topicService;

    private final SubscriptionService subscriptionService;

    private final KafkaClusters kafkaClusters;

    private final ApplicationsService applicationsService;

    private final GalapagosTopicConfig topicConfig;

    @Autowired
    public ValidatingTopicServiceImpl(@Qualifier(value = "nonvalidating") TopicService topicService,
            SubscriptionService subscriptionService, ApplicationsService applicationsService,
            KafkaClusters kafkaClusters, GalapagosTopicConfig topicConfig) {
        this.topicService = topicService;
        this.subscriptionService = subscriptionService;
        this.applicationsService = applicationsService;
        this.kafkaClusters = kafkaClusters;
        this.topicConfig = topicConfig;
    }

    @Override
    public CompletableFuture<TopicMetadata> createTopic(String environmentId, TopicMetadata topic,
            Integer partitionCount, Map<String, String> topicConfig) {
        return checkOnNonStaging(environmentId, "create topics", TopicMetadata.class)
                .orElseGet(() -> topicService.createTopic(environmentId, topic, partitionCount, topicConfig));
    }

    @Override
    public boolean canDeleteTopic(String environmentId, String topicName) {
        TopicMetadata topic = getTopic(environmentId, topicName).orElse(null);

        if (topic == null) {
            return false;
        }

        if (topic.getType() == TopicType.INTERNAL) {
            return kafkaClusters.getEnvironmentMetadata(environmentId).map(env -> !env.isStagingOnly()).orElse(false);
        }

        LocalDate eolDate = topic.getEolDate();
        boolean isEolDatePast = eolDate != null && eolDate.isBefore(LocalDate.now());

        if (!subscriptionService.getSubscriptionsForTopic(environmentId, topicName, false).isEmpty()
                && !isEolDatePast) {
            return false;
        }

        String nextEnvId = nextStageId(environmentId).orElse(null);
        if (nextEnvId == null) {
            return true;
        }

        return topicService.getTopic(nextEnvId, topicName).isEmpty();
    }

    @Override
    public CompletableFuture<Void> deleteLatestTopicSchemaVersion(String environmentId, String topicName) {
        TopicMetadata topic = getTopic(environmentId, topicName).orElse(null);

        if (topic == null) {
            return CompletableFuture.failedFuture(new NoSuchElementException(
                    "No topic with name " + topicName + " found on environment " + environmentId + "."));
        }

        if (!subscriptionService.getSubscriptionsForTopic(environmentId, topicName, false).isEmpty()) {
            return CompletableFuture
                    .failedFuture(new IllegalStateException("Schemas of subscribed Topics cannot be deleted!"));
        }

        return topicService.deleteLatestTopicSchemaVersion(environmentId, topicName);
    }

    @Override
    public CompletableFuture<Void> deleteTopic(String environmentId, String topicName) {
        if (!canDeleteTopic(environmentId, topicName)) {
            return CompletableFuture.failedFuture(new TopicInUseException(
                    "The topic is currently in use by at least one application (other than owner application) and / or has been staged and thus cannot be deleted."));
        }

        return topicService.deleteTopic(environmentId, topicName);
    }

    @Override
    public CompletableFuture<Void> updateTopicDescription(String environmentId, String topicName,
            String newDescription) {
        return checkOnNonStaging(environmentId, "update topic descriptions")
                .orElseGet(() -> topicService.updateTopicDescription(environmentId, topicName, newDescription));
    }

    @Override
    public CompletableFuture<Void> markTopicDeprecated(String topicName, String deprecationText, LocalDate eolDate) {
        if (eolDate.isBefore(LocalDate.now().plus(topicConfig.getMinDeprecationTime()))) {
            return CompletableFuture
                    .failedFuture(new IllegalArgumentException("EOL date for deprecated topic must be at least "
                            + toDisplayString(topicConfig.getMinDeprecationTime()) + " in the future"));
        }

        return topicService.markTopicDeprecated(topicName, deprecationText, eolDate);
    }

    @Override
    public CompletableFuture<Void> unmarkTopicDeprecated(String topicName) {
        return topicService.unmarkTopicDeprecated(topicName);
    }

    @Override
    public CompletableFuture<Void> setSubscriptionApprovalRequiredFlag(String environmentId, String topicName,
            boolean subscriptionApprovalRequired) {
        return checkOnNonStaging(environmentId, "update subscriptionApprovalRequired flag").orElseGet(() -> topicService
                .setSubscriptionApprovalRequiredFlag(environmentId, topicName, subscriptionApprovalRequired));
    }

    @Override
    public CompletableFuture<SchemaMetadata> addTopicSchemaVersion(String environmentId, String topicName,
            String jsonSchema, String changeDescription) {
        return checkOnNonStaging(environmentId, "add JSON schemas", SchemaMetadata.class).orElseGet(
                () -> topicService.addTopicSchemaVersion(environmentId, topicName, jsonSchema, changeDescription));
    }

    @Override
    public CompletableFuture<SchemaMetadata> addTopicSchemaVersion(String environmentId, SchemaMetadata metadata) {
        return topicService.addTopicSchemaVersion(environmentId, metadata);
    }

    private Optional<CompletableFuture<Void>> checkOnNonStaging(String environmentId, String action) {
        return checkOnNonStaging(environmentId, action, Void.class);
    }

    private <T> Optional<CompletableFuture<T>> checkOnNonStaging(String environmentId, String action,
            Class<T> resultClass) {
        if (kafkaClusters.getEnvironmentMetadata(environmentId).map(KafkaEnvironmentConfig::isStagingOnly)
                .orElse(false)) {
            return Optional.of(CompletableFuture.failedFuture(new IllegalStateException("You may only " + action
                    + " on non-staging-only environments. Use Staging to apply such a change on this environment.")));
        }
        return Optional.empty();
    }

    private Optional<String> nextStageId(String environmentId) {
        List<String> environmentIds = kafkaClusters.getEnvironmentIds();

        for (int i = 0; i < environmentIds.size() - 1; i++) {
            if (environmentId.equals(environmentIds.get(i))) {
                return Optional.of(environmentIds.get(i + 1));
            }
        }

        return Optional.empty();
    }

    @Override
    public List<TopicMetadata> listTopics(String environmentId) {
        return topicService.listTopics(environmentId);
    }

    @Override
    public Optional<TopicMetadata> getTopic(String environmentId, String topicName) {
        return topicService.getTopic(environmentId, topicName);
    }

    @Override
    public List<SchemaMetadata> getTopicSchemaVersions(String environmentId, String topicName) {
        return topicService.getTopicSchemaVersions(environmentId, topicName);
    }

    @Override
    public Optional<SchemaMetadata> getSchemaById(String environmentId, String schemaId) {
        return topicService.getSchemaById(environmentId, schemaId);
    }

    @Override
    public CompletableFuture<TopicCreateParams> buildTopicCreateParams(String environmentId, String topicName) {
        return topicService.buildTopicCreateParams(environmentId, topicName);
    }

    @Override
    public CompletableFuture<List<ConsumerRecord<String, String>>> peekTopicData(String environmentId, String topicName,
            int limit) {
        TopicMetadata metadata = getTopic(environmentId, topicName).orElse(null);

        // if metadata is null, topicService implementation will deal with it.
        if (metadata != null && metadata.isSubscriptionApprovalRequired()
                && !currentUserMayRead(environmentId, metadata)) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "You are not permitted to read from this topic. Subscribe one of your applications to this topic first."));
        }

        return topicService.peekTopicData(environmentId, topicName, limit);
    }

    private boolean currentUserMayRead(String environmentId, TopicMetadata metadata) {
        Set<String> subscribedApplications = subscriptionService
                .getSubscriptionsForTopic(environmentId, metadata.getName(), false).stream()
                .map(SubscriptionMetadata::getClientApplicationId).collect(Collectors.toSet());
        subscribedApplications.add(metadata.getOwnerApplicationId());

        return applicationsService.getUserApplicationOwnerRequests().stream()
                .anyMatch(r -> subscribedApplications.contains(r.getApplicationId()));
    }

    private static String toDisplayString(Period period) {
        // TODO rework for i18n
        StringBuilder sb = new StringBuilder();

        Function<Integer, String> plural = i -> i > 1 ? "s" : "";
        Function<StringBuilder, String> comma = s -> s.length() > 0 ? ", " : "";

        if (period.getYears() > 0) {
            sb.append(comma.apply(sb));
            sb.append(period.getYears()).append(" year").append(plural.apply(period.getYears()));
        }
        if (period.getMonths() > 0) {
            sb.append(comma.apply(sb));
            sb.append(period.getMonths()).append(" month").append(plural.apply(period.getMonths()));
        }
        if (period.getDays() > 0) {
            sb.append(comma.apply(sb));
            sb.append(period.getDays()).append(" day").append(plural.apply(period.getDays()));
        }

        return sb.toString();
    }
}
