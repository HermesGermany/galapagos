package com.hermesworld.ais.galapagos.topics.service.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.events.GalapagosEventManager;
import com.hermesworld.ais.galapagos.events.GalapagosEventSink;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.kafka.util.InitPerCluster;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.naming.InvalidTopicNameException;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.schemas.IncompatibleSchemaException;
import com.hermesworld.ais.galapagos.schemas.SchemaUtil;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.topics.*;
import com.hermesworld.ais.galapagos.topics.config.GalapagosTopicConfig;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.everit.json.schema.Schema;
import org.everit.json.schema.SchemaException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Qualifier(value = "nonvalidating")
@Slf4j
public class TopicServiceImpl implements TopicService, InitPerCluster {

    private final KafkaClusters kafkaClusters;

    private final NamingService namingService;

    private final CurrentUserService userService;

    private final ApplicationsService applicationsService;

    private final GalapagosTopicConfig topicSettings;

    private final GalapagosEventManager eventManager;

    private static final Comparator<TopicMetadata> topicsComparator = Comparator.comparing(TopicMetadata::getName);

    private static final Comparator<SchemaMetadata> schemaVersionsComparator = Comparator
            .comparingInt(SchemaMetadata::getSchemaVersion);

    static final String METADATA_TOPIC_NAME = "topics";

    static final String SCHEMA_TOPIC_NAME = "schemas";

    public TopicServiceImpl(KafkaClusters kafkaClusters, ApplicationsService applicationsService,
            NamingService namingService, CurrentUserService userService, GalapagosTopicConfig topicSettings,
            GalapagosEventManager eventManager) {
        this.kafkaClusters = kafkaClusters;
        this.applicationsService = applicationsService;
        this.namingService = namingService;
        this.userService = userService;
        this.topicSettings = topicSettings;
        this.eventManager = eventManager;
    }

    @Override
    public void init(KafkaCluster cluster) {
        getTopicRepository(cluster).getObjects();
        getSchemaRepository(cluster).getObjects();
    }

    @Override
    public CompletableFuture<TopicMetadata> createTopic(String environmentId, TopicMetadata topic,
            Integer partitionCount, Map<String, String> topicConfig) {
        KnownApplication ownerApplication = applicationsService.getKnownApplication(topic.getOwnerApplicationId())
                .orElse(null);
        if (ownerApplication == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Unknown application ID: " + topic.getOwnerApplicationId()));
        }

        KafkaCluster environment = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (environment == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        ApplicationMetadata metadata = applicationsService
                .getApplicationMetadata(environmentId, topic.getOwnerApplicationId()).orElse(null);
        if (metadata == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Application "
                    + topic.getOwnerApplicationId() + " is not registered on environment " + environmentId));
        }

        if (!applicationsService.isUserAuthorizedFor(metadata.getApplicationId())) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Current user is no owner of application " + metadata.getApplicationId()));
        }

        try {
            namingService.validateTopicName(topic.getName(), topic.getType(), ownerApplication);
        }
        catch (InvalidTopicNameException e) {
            return CompletableFuture.failedFuture(e);
        }

        GalapagosEventSink eventSink = eventManager.newEventSink(environment);

        return environment.getActiveBrokerCount().thenCompose(brokerCount -> {

            int replicationFactor = (topic.getType() != TopicType.INTERNAL
                    && topic.getCriticality() == Criticality.CRITICAL) ? topicSettings.getCriticalReplicationFactor()
                            : topicSettings.getStandardReplicationFactor();

            if (brokerCount < replicationFactor) {
                replicationFactor = brokerCount;
            }

            Integer pc = partitionCount;

            if (pc == null || pc < 1 || pc > topicSettings.getMaxPartitionCount()) {
                pc = topicSettings.getDefaultPartitionCount();
            }

            TopicCreateParams createParams = new TopicCreateParams(pc, replicationFactor);
            topicConfig.forEach(createParams::setTopicConfig);

            TopicBasedRepository<TopicMetadata> topicRepository = environment.getRepository(METADATA_TOPIC_NAME,
                    TopicMetadata.class);
            return environment.createTopic(topic.getName(), createParams).thenCompose(o -> topicRepository.save(topic))
                    .thenCompose(o -> eventSink.handleTopicCreated(topic, createParams)).thenApply(o -> topic);
        });
    }

    @Override
    public CompletableFuture<Void> addTopicProducer(String environmentId, String topicName, String producerId) {
        return doWithClusterAndTopic(environmentId, topicName, (kafkaCluster, metadata, eventSink) -> {
            if (metadata.getType() == TopicType.COMMANDS) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("For Command Topics, subscribe to the Topic to add a new Producer"));
            }
            List<String> producerList = new ArrayList<>(metadata.getProducers());
            producerList.add(producerId);
            metadata.setProducers(producerList);
            TopicMetadata newTopic = new TopicMetadata(metadata);
            return getTopicRepository(kafkaCluster).save(newTopic)
                    .thenCompose(o -> eventSink.handleAddTopicProducer(newTopic, producerId));
        });

    }

    @Override
    public CompletableFuture<Void> removeTopicProducer(String envId, String topicName, String producerId) {
        return doWithClusterAndTopic(envId, topicName, (kafkaCluster, metadata, eventSink) -> {
            if (metadata.getType() == TopicType.COMMANDS) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("For Command Topics, subscribe to the Topic to remove a Producer"));
            }
            List<String> producerList = new ArrayList<>(metadata.getProducers());
            producerList.remove(producerId);
            metadata.setProducers(producerList);
            TopicMetadata newTopic = new TopicMetadata(metadata);
            return getTopicRepository(kafkaCluster).save(newTopic)
                    .thenCompose(o -> eventSink.handleRemoveTopicProducer(newTopic, producerId));
        });

    }

    @Override
    public CompletableFuture<Void> changeTopicOwner(String environmentId, String topicName,
            String newApplicationOwnerId) {
        return doOnAllStages(topicName, (kafkaCluster, metadata, eventSink) -> {
            if (metadata.getType() == TopicType.INTERNAL) {
                return CompletableFuture
                        .failedFuture(new IllegalStateException("Cannot change owner for internal topics"));
            }
            String previousOwnerApplicationId = metadata.getOwnerApplicationId();
            List<String> producerList = new ArrayList<>(metadata.getProducers());
            producerList.add(metadata.getOwnerApplicationId());
            metadata.setOwnerApplicationId(newApplicationOwnerId);
            producerList.remove(newApplicationOwnerId);
            metadata.setProducers(producerList);
            TopicMetadata newTopic = new TopicMetadata(metadata);
            return getTopicRepository(kafkaCluster).save(newTopic)
                    .thenCompose(o -> eventSink.handleTopicOwnerChanged(newTopic, previousOwnerApplicationId));
        });
    }

    @Override
    public boolean canDeleteTopic(String environmentId, String topicName) {
        // business checks in ValidatingTopicServiceImpl
        return getTopic(environmentId, topicName).isPresent();
    }

    @Override
    public CompletableFuture<Void> deleteTopic(String environmentId, String topicName) {
        return doWithClusterAndTopic(environmentId, topicName,
                (kafkaCluster, metadata, eventSink) -> kafkaCluster.deleteTopic(topicName)
                        .thenCompose(o -> getTopicRepository(kafkaCluster).delete(metadata))
                        .thenCompose(o -> deleteTopicSchemas(kafkaCluster, topicName))
                        .thenCompose(o -> eventSink.handleTopicDeleted(metadata)));
    }

    @Override
    public CompletableFuture<Void> updateTopicDescription(String environmentId, String topicName,
            String newDescription) {
        return doWithClusterAndTopic(environmentId, topicName, (kafkaCluster, metadata, eventSink) -> {
            TopicMetadata newMeta = new TopicMetadata(metadata);
            newMeta.setDescription(newDescription);

            return getTopicRepository(kafkaCluster).save(newMeta)
                    .thenCompose(o -> eventSink.handleTopicDescriptionChanged(newMeta));
        });
    }

    @Override
    public CompletableFuture<Void> markTopicDeprecated(String topicName, String deprecationText, LocalDate eolDate) {
        return doOnAllStages(topicName, (kafkaCluster, metadata, eventSink) -> {
            TopicMetadata newMeta = new TopicMetadata(metadata);
            newMeta.setDeprecated(true);
            newMeta.setDeprecationText(deprecationText);
            newMeta.setEolDate(eolDate);

            return getTopicRepository(kafkaCluster).save(newMeta)
                    .thenCompose(o2 -> eventSink.handleTopicDeprecated(newMeta));
        });
    }

    @Override
    public CompletableFuture<Void> unmarkTopicDeprecated(String topicName) {
        return doOnAllStages(topicName, (kafkaCluster, metadata, eventSink) -> {
            TopicMetadata newMeta = new TopicMetadata(metadata);
            newMeta.setDeprecated(false);
            newMeta.setDeprecationText(null);
            newMeta.setEolDate(null);

            return getTopicRepository(kafkaCluster).save(newMeta)
                    .thenCompose(o2 -> eventSink.handleTopicUndeprecated(newMeta));
        });
    }

    @Override
    public CompletableFuture<Void> setSubscriptionApprovalRequiredFlag(String environmentId, String topicName,
            boolean subscriptionApprovalRequired) {
        return doWithClusterAndTopic(environmentId, topicName, (kafkaCluster, metadata, eventSink) -> {
            if (metadata.isSubscriptionApprovalRequired() == subscriptionApprovalRequired) {
                return FutureUtil.noop();
            }

            if (metadata.getType() == TopicType.INTERNAL) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Cannot update subscriptionApprovalRequired flag for application internal topics"));
            }

            TopicMetadata newMeta = new TopicMetadata(metadata);
            newMeta.setSubscriptionApprovalRequired(subscriptionApprovalRequired);

            return getTopicRepository(kafkaCluster).save(newMeta)
                    .thenCompose(o -> eventSink.handleTopicSubscriptionApprovalRequiredFlagChanged(newMeta));
        });
    }

    @Override
    public Optional<TopicMetadata> getTopic(String environmentId, String topicName) {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return Optional.empty();
        }

        return getTopicRepository(kafkaCluster).getObject(topicName);
    }

    @Override
    public List<TopicMetadata> listTopics(String environmentId) {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return Collections.emptyList();
        }

        return getTopicRepository(kafkaCluster).getObjects().stream().sorted(topicsComparator)
                .collect(Collectors.toList());
    }

    @Override
    public List<SchemaMetadata> getTopicSchemaVersions(String environmentId, String topicName) {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return Collections.emptyList();
        }

        return getSchemaRepository(kafkaCluster).getObjects().stream()
                .filter(meta -> topicName.equals(meta.getTopicName())).sorted(schemaVersionsComparator)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<SchemaMetadata> getSchemaById(String environmentId, String schemaId) {
        return kafkaClusters.getEnvironment(environmentId)
                .flatMap(cluster -> getSchemaRepository(cluster).getObject(schemaId));
    }

    @Override
    public CompletableFuture<SchemaMetadata> addTopicSchemaVersion(String environmentId, String topicName,
            String jsonSchema, String changeDescription, SchemaCompatCheckMode skipCompatCheck) {
        String userName = userService.getCurrentUserName().orElse(null);
        if (userName == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No user currently logged in"));
        }

        int nextVersionNo = 1;

        List<SchemaMetadata> existingVersions = getTopicSchemaVersions(environmentId, topicName);

        if (!existingVersions.isEmpty()) {
            SchemaMetadata previousVersion = existingVersions.get(existingVersions.size() - 1);
            nextVersionNo = previousVersion.getSchemaVersion() + 1;
        }

        SchemaMetadata newSchemaVersion = new SchemaMetadata();
        // if it will replace an existing version, the private method will update the ID
        newSchemaVersion.setId(UUID.randomUUID().toString());
        newSchemaVersion.setTopicName(topicName);
        newSchemaVersion.setCreatedBy(userName);
        newSchemaVersion.setCreatedAt(ZonedDateTime.now());
        newSchemaVersion.setJsonSchema(jsonSchema);
        newSchemaVersion.setChangeDescription(changeDescription);
        newSchemaVersion.setSchemaVersion(nextVersionNo);

        return addTopicSchemaVersion(environmentId, newSchemaVersion, skipCompatCheck);
    }

    @Override
    public CompletableFuture<Void> deleteLatestTopicSchemaVersion(String environmentId, String topicName) {

        List<SchemaMetadata> existingVersions = getTopicSchemaVersions(environmentId, topicName);
        String nextEnvId = nextStageId(environmentId).orElse(null);
        if (existingVersions.isEmpty()) {
            return CompletableFuture
                    .failedFuture(new IllegalStateException("No Schemas on current stage for topic " + topicName));
        }
        SchemaMetadata latestSchemaOnCurrentStage = existingVersions.get(existingVersions.size() - 1);

        SchemaMetadata schemaOnNextStage = getTopicSchemaVersions(nextEnvId, topicName).stream()
                .filter(v -> latestSchemaOnCurrentStage.getSchemaVersion() == v.getSchemaVersion()).findFirst()
                .orElse(null);

        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        if (schemaOnNextStage != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("""
                    The selected schema already exists on the next stage! To delete \
                    this schema you have to delete it there first!\
                    """));
        }

        GalapagosEventSink eventSink = eventManager.newEventSink(kafkaCluster);
        TopicMetadata metadata = getTopicRepository(kafkaCluster).getObject(topicName).orElse(null);
        if (metadata == null) {
            return noSuchTopic(environmentId, topicName);
        }
        return getSchemaRepository(kafkaCluster).delete(latestSchemaOnCurrentStage)
                .thenCompose(o -> eventSink.handleTopicSchemaDeleted(metadata));
    }

    @Override
    public CompletableFuture<SchemaMetadata> addTopicSchemaVersion(String environmentId, SchemaMetadata schemaMetadata,
            SchemaCompatCheckMode skipCompatCheck) {
        String userName = userService.getCurrentUserName().orElse(null);
        if (userName == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No user currently logged in"));
        }

        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        String topicName = schemaMetadata.getTopicName();

        TopicMetadata metadata = getTopicRepository(kafkaCluster).getObject(topicName).orElse(null);
        if (metadata == null) {
            return noSuchTopic(environmentId, topicName);
        }

        if (metadata.getType() == TopicType.INTERNAL) {
            return CompletableFuture
                    .failedFuture(new IllegalStateException("Cannot add JSON schemas to internal topics"));
        }

        List<SchemaMetadata> existingVersions = getTopicSchemaVersions(environmentId, topicName);

        Schema newSchema;
        try {
            newSchema = compileSchema(schemaMetadata.getJsonSchema());

        }
        catch (JSONException | SchemaException e) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Could not parse JSON schema", e));
        }

        JSONObject json = new JSONObject(schemaMetadata.getJsonSchema());
        if (!json.has("$schema")) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("The JSON Schema must declare a \"$schema\" value on first level."));
        }

        if (newSchema.definesProperty("data")
                && (metadata.getType() == TopicType.EVENTS || metadata.getType() == TopicType.COMMANDS)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    """
                            The JSON Schema must not declare a "data" object on first level.\
                             The JSON Schema must not contain the CloudEvents fields, but only the contents of the "data" field.\
                            """));
        }

        if (existingVersions.isEmpty() && schemaMetadata.getSchemaVersion() != 1) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Illegal next schema version number #"
                    + schemaMetadata.getSchemaVersion() + " for topic " + topicName));
        }
        if (!existingVersions.isEmpty() && existingVersions.get(existingVersions.size() - 1)
                .getSchemaVersion() != schemaMetadata.getSchemaVersion() - 1) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Illegal next schema version number #"
                    + schemaMetadata.getSchemaVersion() + " for topic " + topicName));
        }

        SchemaMetadata previousVersion = existingVersions.isEmpty() ? null
                : existingVersions.get(existingVersions.size() - 1);

        if (previousVersion != null && skipCompatCheck == SchemaCompatCheckMode.CHECK_SCHEMA) {
            try {
                Schema previousSchema = compileSchema(previousVersion.getJsonSchema());

                // additional test: if both are equal, do not accept (save a tree!)
                if (SchemaUtil.areEqual(newSchema, previousSchema)) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException(
                            "The new schema is identical to the latest schema of this topic."));
                }

                boolean reverse = metadata.getType() == TopicType.COMMANDS;

                SchemaUtil.verifyCompatibleTo(reverse ? newSchema : previousSchema,
                        reverse ? previousSchema : newSchema);

            }
            catch (JSONException e) {
                // how, on earth, did it get into the repo then???
                log.error("Invalid JSON schema in repository found for topic " + topicName + " on environment "
                        + environmentId + " with schema version " + previousVersion.getSchemaVersion());

                // danger zone here: allow full replacement of invalid schema (fallthrough)
            }
            catch (IncompatibleSchemaException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        if (existingVersions.isEmpty() && schemaMetadata.getChangeDescription() != null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Cant have a change description for schema with version number #1."));
        }

        if (!existingVersions.isEmpty() && schemaMetadata.getChangeDescription() == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Change Description has to be set for schemas with version greater 1."));
        }

        // copy to be safe here
        SchemaMetadata newSchemaVersion = new SchemaMetadata(schemaMetadata);

        GalapagosEventSink eventSink = eventManager.newEventSink(kafkaCluster);

        return getSchemaRepository(kafkaCluster).save(newSchemaVersion)
                .thenCompose(o -> eventSink.handleTopicSchemaAdded(metadata, newSchemaVersion))
                .thenApply(v -> newSchemaVersion);
    }

    @Override
    public CompletableFuture<TopicCreateParams> buildTopicCreateParams(String environmentId, String topicName) {
        return kafkaClusters.getEnvironment(environmentId).map(cluster -> cluster.buildTopicCreateParams(topicName))
                .orElse(FutureUtil.noSuchEnvironment(environmentId));
    }

    @Override
    public CompletableFuture<List<ConsumerRecord<String, String>>> peekTopicData(String environmentId, String topicName,
            int limit) {
        // only allow peek of API topics
        TopicMetadata metadata = getTopic(environmentId, topicName).orElse(null);
        if (metadata == null) {
            return noSuchTopic(environmentId, topicName);
        }
        if (metadata.getType() == TopicType.INTERNAL) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Data of internal topics cannot be retrieved via Galapagos."));
        }

        return kafkaClusters.getEnvironment(environmentId).map(cluster -> cluster.peekTopicData(topicName, limit))
                .orElse(FutureUtil.noSuchEnvironment(environmentId));
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

    private TopicBasedRepository<TopicMetadata> getTopicRepository(KafkaCluster kafkaCluster) {
        return kafkaCluster.getRepository(METADATA_TOPIC_NAME, TopicMetadata.class);
    }

    private TopicBasedRepository<SchemaMetadata> getSchemaRepository(KafkaCluster kafkaCluster) {
        return kafkaCluster.getRepository(SCHEMA_TOPIC_NAME, SchemaMetadata.class);
    }

    private CompletableFuture<Void> doWithClusterAndTopic(String environmentId, String topicName,
            TopicServiceAction action) {
        KafkaCluster kafkaCluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (kafkaCluster == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        TopicMetadata metadata = getTopic(environmentId, topicName).orElse(null);
        if (metadata == null) {
            return noSuchTopic(environmentId, topicName);
        }

        GalapagosEventSink eventSink = eventManager.newEventSink(kafkaCluster);

        return action.apply(kafkaCluster, metadata, eventSink);
    }

    private CompletableFuture<Void> doOnAllStages(String topicName, TopicServiceAction action) {
        List<String> environmentIds = kafkaClusters.getEnvironmentIds();

        // only operate on environments where this topic exists
        environmentIds = environmentIds.stream()
                .filter(env -> kafkaClusters.getEnvironment(env)
                        .map(cluster -> getTopicRepository(cluster).containsObject(topicName)).orElse(false))
                .collect(Collectors.toList());

        if (environmentIds.isEmpty()) {
            return CompletableFuture
                    .failedFuture(new NoSuchElementException("Topic " + topicName + " not found on any environment"));
        }

        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);

        // Build Event Sinks here to avoid missing Thread-Local issues
        Map<String, GalapagosEventSink> eventSinks = new HashMap<>();
        environmentIds.stream().map(kafkaClusters::getEnvironment).filter(Optional::isPresent)
                .forEach(op -> eventSinks.put(op.get().getId(), eventManager.newEventSink(op.get())));

        // perform action on one environment after another. This could theoretically be optimized to parallel execution,
        // but this way, one failure on one stage at least avoids deprecation on the next stages.
        for (String envId : environmentIds) {
            TopicServiceAction wrappedAction = (cluster, metadata, sink) -> action.apply(cluster, metadata,
                    eventSinks.get(envId));
            result = result.thenCompose(o -> doWithClusterAndTopic(envId, topicName, wrappedAction));
        }

        return result;
    }

    private CompletableFuture<Void> deleteTopicSchemas(KafkaCluster cluster, String topicName) {
        CompletableFuture<Void> result = FutureUtil.noop();
        TopicBasedRepository<SchemaMetadata> schemaRepository = getSchemaRepository(cluster);
        for (SchemaMetadata schema : schemaRepository.getObjects()) {
            if (topicName.equals(schema.getTopicName())) {
                result = result.thenCompose(o -> schemaRepository.delete(schema));
            }
        }
        return result;
    }

    private static Schema compileSchema(String source) {
        JSONObject obj = new JSONObject(source);
        return SchemaLoader.builder().draftV7Support().schemaJson(obj).build().load().build();
    }

    private static <T> CompletableFuture<T> noSuchTopic(String environmentId, String topicName) {
        return CompletableFuture.failedFuture(new NoSuchElementException(
                "No topic with name " + topicName + " found on environment " + environmentId + "."));
    }

    private interface TopicServiceAction {
        CompletableFuture<Void> apply(KafkaCluster cluster, TopicMetadata topic, GalapagosEventSink eventSink);
    }

}
