package com.hermesworld.ais.galapagos.staging.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.changes.ApplicableChange;
import com.hermesworld.ais.galapagos.changes.ApplyChangeContext;
import com.hermesworld.ais.galapagos.changes.Change;
import com.hermesworld.ais.galapagos.changes.ChangeType;
import com.hermesworld.ais.galapagos.changes.impl.ChangeBase;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.staging.Staging;
import com.hermesworld.ais.galapagos.staging.StagingResult;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public final class StagingImpl implements Staging, ApplyChangeContext {

    private final String applicationId;

    private final String sourceEnvironmentId;

    private final String targetEnvironmentId;

    private final TopicService topicService;

    private final SubscriptionService subscriptionService;

    private List<ApplicableChange> changes;

    private StagingImpl(String applicationId, String sourceEnvironmentId, String targetEnvironmentId,
            TopicService topicService, SubscriptionService subscriptionService) {
        this.applicationId = applicationId;
        this.sourceEnvironmentId = sourceEnvironmentId;
        this.targetEnvironmentId = targetEnvironmentId;
        this.topicService = topicService;
        this.subscriptionService = subscriptionService;
    }

    @Override
    public String getApplicationId() {
        return applicationId;
    }

    @Override
    public String getSourceEnvironmentId() {
        return sourceEnvironmentId;
    }

    @Override
    public String getTargetEnvironmentId() {
        return targetEnvironmentId;
    }

    @Override
    public TopicService getTopicService() {
        return topicService;
    }

    @Override
    public SubscriptionService getSubscriptionService() {
        return subscriptionService;
    }

    @Override
    public List<Change> getChanges() {
        return Collections.unmodifiableList(changes);
    }

    @Override
    public List<StagingResult> perform() {
        if (changes.isEmpty()) {
            return Collections.emptyList();
        }

        List<StagingResult> result = new ArrayList<>();
        for (ApplicableChange change : changes) {
            try {
                change.applyTo(this).get();
                result.add(new StagingResult(change, true, null));
            }
            catch (ExecutionException e) {
                log.info("Staging was not successful due to exception: ", e.getCause());
                result.add(new StagingResult(change, false, e.getCause().getMessage()));
            }
            catch (InterruptedException e) {
                return result;
            }
        }

        return result;
    }

    public static CompletableFuture<StagingImpl> build(String applicationId, String sourceEnvironmentId,
            String targetEnvironmentId, List<Change> changesFilter, TopicService topicService,
            SubscriptionService subscriptionService) {
        StagingImpl staging = new StagingImpl(applicationId, sourceEnvironmentId, targetEnvironmentId, topicService,
                subscriptionService);

        List<ApplicableChange> changes = new ArrayList<>();

        List<TopicMetadata> sourceTopics = topicService.listTopics(sourceEnvironmentId).stream()
                .filter(t -> applicationId.equals(t.getOwnerApplicationId())).collect(Collectors.toList());
        List<TopicMetadata> targetTopics = topicService.listTopics(targetEnvironmentId).stream()
                .filter(t -> applicationId.equals(t.getOwnerApplicationId())).collect(Collectors.toList());

        Set<String> createdApiTopics = new HashSet<>();
        try {
            compareSourceTarget(sourceTopics, targetTopics, TopicMetadata::getName, t -> {
                ApplicableChange change = checkForCreateTopic(t, sourceEnvironmentId, topicService);
                if (change.getChangeType() == ChangeType.COMPOUND_CHANGE) {
                    createdApiTopics.add(t.getName());
                }
                changes.add(change);
            }, t -> changes.add(ChangeBase.deleteTopic(t.getName(), t.getType() == TopicType.INTERNAL)),
                    (t1, t2) -> changes.addAll(checkForTopicChanges(t1, t2)));
        }
        catch (CompletionException e) {
            return CompletableFuture.failedFuture(e);
        }

        List<SubscriptionMetadata> sourceSubs = subscriptionService.getSubscriptionsOfApplication(sourceEnvironmentId,
                applicationId, false);
        List<SubscriptionMetadata> targetSubs = subscriptionService.getSubscriptionsOfApplication(targetEnvironmentId,
                applicationId, false);
        compareSourceTarget(sourceSubs, targetSubs, StagingImpl::buildSubscriptionId,
                s -> changes.add(ChangeBase.subscribeTopic(s)), s -> changes.add(ChangeBase.unsubscribeTopic(s)), null);

        for (TopicMetadata topic : sourceTopics) {
            boolean isCreatedApiTopic = createdApiTopics.contains(topic.getName());
            List<SchemaMetadata> sourceSchemas = topicService.getTopicSchemaVersions(sourceEnvironmentId,
                    topic.getName());
            List<SchemaMetadata> targetSchemas = topicService.getTopicSchemaVersions(targetEnvironmentId,
                    topic.getName());

            // created API topics cannot have targetSchemas. Also, we can safely skip the first schema here,
            // because it has been added in the createTopic change (a compound change).
            if (isCreatedApiTopic && !sourceSchemas.isEmpty()) {
                sourceSchemas = sourceSchemas.subList(1, sourceSchemas.size());
            }

            compareSourceTarget(sourceSchemas, targetSchemas, StagingImpl::buildSchemaId,
                    s -> changes.add(ChangeBase.publishTopicSchemaVersion(topic.getName(), s)), null, null);
        }

        if (changesFilter != null && !changesFilter.isEmpty()) {
            addVirtualChanges(changesFilter, changes);

            // noinspection SuspiciousMethodCalls
            changes.retainAll(changesFilter);
        }

        staging.changes = changes;

        return CompletableFuture.completedFuture(staging);
    }

    private static ApplicableChange checkForCreateTopic(TopicMetadata topic, String sourceEnvironmentId,
            TopicService topicService) throws CompletionException {
        SchemaMetadata firstSchema = null;
        if (topic.getType() != TopicType.INTERNAL) {
            if (topic.isDeprecated()) {
                return new CannotStageDeprecatedTopicChange(topic);
            }
            List<SchemaMetadata> schemas = topicService.getTopicSchemaVersions(sourceEnvironmentId, topic.getName());
            if (schemas.isEmpty()) {
                return new ApiTopicMissesSchemaChange(topic);
            }
            firstSchema = schemas.get(0);
        }

        TopicCreateParams createParams = topicService.buildTopicCreateParams(sourceEnvironmentId, topic.getName())
                .join();

        ChangeBase createChange = ChangeBase.createTopic(topic, createParams);
        if (topic.getType() != TopicType.INTERNAL) {
            List<ChangeBase> additionalChanges = new ArrayList<>();
            ChangeBase firstSchemaChange = ChangeBase.publishTopicSchemaVersion(topic.getName(), firstSchema);
            additionalChanges.add(firstSchemaChange);
            for (String producer : topic.getProducers()) {
                additionalChanges.add(ChangeBase.addTopicProducer(topic.getName(), producer));
            }
            return ChangeBase.compoundChange(createChange, additionalChanges);
        }

        return createChange;
    }

    private static List<ApplicableChange> checkForTopicChanges(TopicMetadata oldTopic, TopicMetadata newTopic) {
        List<ApplicableChange> result = new ArrayList<>();
        if (!Objects.equals(oldTopic.getDescription(), newTopic.getDescription())) {
            result.add(ChangeBase.updateTopicDescription(newTopic.getName(), newTopic.getDescription(),
                    newTopic.getType() == TopicType.INTERNAL));
        }

        if ((!oldTopic.isDeprecated() && newTopic.isDeprecated())
                || (newTopic.isDeprecated()
                        && !Objects.equals(oldTopic.getDeprecationText(), newTopic.getDeprecationText()))
                || (newTopic.isDeprecated() && !Objects.equals(oldTopic.getEolDate(), newTopic.getEolDate()))) {
            result.add(ChangeBase.markTopicDeprecated(newTopic.getName(), newTopic.getDeprecationText(),
                    newTopic.getEolDate()));
        }

        if (oldTopic.isDeprecated() && !newTopic.isDeprecated()) {
            result.add(ChangeBase.unmarkTopicDeprecated(newTopic.getName()));
        }

        if (oldTopic.isSubscriptionApprovalRequired() != newTopic.isSubscriptionApprovalRequired()) {
            result.add(ChangeBase.updateTopicSubscriptionApprovalRequiredFlag(newTopic.getName(),
                    newTopic.isSubscriptionApprovalRequired()));
        }

        List<String> oldProducers = Optional.ofNullable(oldTopic.getProducers()).orElse(List.of());
        List<String> newProducers = Optional.ofNullable(newTopic.getProducers()).orElse(List.of());

        if (!oldProducers.equals(newProducers)) {
            List<String> producerIdsToBeAdded = newProducers.stream()
                    .filter(producer -> !oldProducers.contains(producer)).collect(Collectors.toList());

            producerIdsToBeAdded
                    .forEach(producerId -> result.add(ChangeBase.addTopicProducer(newTopic.getName(), producerId)));

            List<String> ids = new ArrayList<>(oldProducers);
            ids.removeAll(newProducers);
            List<String> toBeDeletedIds = new ArrayList<>(ids);

            toBeDeletedIds
                    .forEach(producerId -> result.add(ChangeBase.removeTopicProducer(newTopic.getName(), producerId)));
        }

        return result;
    }

    private static String buildSubscriptionId(SubscriptionMetadata subscription) {
        return subscription.getClientApplicationId() + "-" + subscription.getTopicName();
    }

    private static String buildSchemaId(SchemaMetadata schema) {
        return schema.getTopicName() + "-" + schema.getSchemaVersion();
    }

    private static <T> void compareSourceTarget(Collection<T> source, Collection<T> target,
            Function<T, String> idProvider, Consumer<T> createdHandler, Consumer<T> deletedHandler,
            BiConsumer<T, T> checkForChangesHandler) {
        for (T tSrc : source) {
            String id = idProvider.apply(tSrc);
            T tTgt = target.stream().filter(t -> id.equals(idProvider.apply(t))).findFirst().orElse(null);
            if (tTgt == null) {
                createdHandler.accept(tSrc);
            }
            else if (checkForChangesHandler != null) {
                checkForChangesHandler.accept(tTgt, tSrc);
            }
        }

        if (deletedHandler != null) {
            for (T tTgt : target) {
                String id = idProvider.apply(tTgt);
                T tSrc = source.stream().filter(t -> id.equals(idProvider.apply(t))).findFirst().orElse(null);
                if (tSrc == null) {
                    deletedHandler.accept(tTgt);
                }
            }
        }
    }

    private static void addVirtualChanges(List<Change> changesFilter, List<ApplicableChange> calculatedChanges) {
        // special treatment: If changes filter contains a "CREATE_TOPIC" for which we have a matching
        // VirtualChange change, THAT should be included, so user gets the error message!
        for (Change change : new ArrayList<>(changesFilter)) {
            if (change.getChangeType() == ChangeType.TOPIC_CREATED) {
                ObjectMapper mapper = JsonUtil.newObjectMapper();
                try {
                    String json1 = mapper.writeValueAsString(change);
                    for (Change change2 : calculatedChanges) {
                        if (change2 instanceof VirtualChange) {
                            String json2 = mapper.writeValueAsString(change2);
                            if (json1.equals(json2)) {
                                changesFilter.add(change2);
                            }
                        }
                    }
                }
                catch (JsonProcessingException e) {
                    // ignore here; just continue with next change
                }
            }
        }
    }

    /**
     * Marker interface for changes which are "virtual", so are not automatically deserialized from JSON. See below
     * classes for examples.
     */
    private interface VirtualChange extends ApplicableChange {
    }

    /**
     * Dummy change class which always fails, because there is no published schema for the API topic.
     */
    @JsonSerialize
    static class ApiTopicMissesSchemaChange implements VirtualChange {

        private final TopicMetadata topicMetadata;

        public ApiTopicMissesSchemaChange(TopicMetadata topicMetadata) {
            this.topicMetadata = topicMetadata;
        }

        public TopicMetadata getTopicMetadata() {
            return topicMetadata;
        }

        @Override
        public ChangeType getChangeType() {
            return ChangeType.TOPIC_CREATED;
        }

        @Override
        public CompletableFuture<?> applyTo(ApplyChangeContext context) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("API Topic cannot be staged without a published JSON schema"));
        }

    }

    /**
     * Dummy change class which always fails, because deprecated topics cannot be staged.
     */
    @JsonSerialize
    static class CannotStageDeprecatedTopicChange implements VirtualChange {

        private final TopicMetadata topicMetadata;

        public CannotStageDeprecatedTopicChange(TopicMetadata topicMetadata) {
            this.topicMetadata = topicMetadata;
        }

        public TopicMetadata getTopicMetadata() {
            return topicMetadata;
        }

        @Override
        public ChangeType getChangeType() {
            return ChangeType.TOPIC_CREATED;
        }

        @Override
        public CompletableFuture<?> applyTo(ApplyChangeContext context) {
            return CompletableFuture.failedFuture(new IllegalStateException("Deprecated topics cannot be staged."));
        }

    }

}
