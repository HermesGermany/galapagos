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
import com.hermesworld.ais.galapagos.messages.MessagesService;
import com.hermesworld.ais.galapagos.messages.MessagesServiceFactory;
import com.hermesworld.ais.galapagos.naming.InvalidTopicNameException;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.schemas.*;
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

    private final MessagesService messagesService;

    private static final Comparator<TopicMetadata> topicsComparator = Comparator.comparing(TopicMetadata::getName);

    private static final Comparator<SchemaMetadata> schemaVersionsComparator = Comparator
            .comparingInt(SchemaMetadata::getSchemaVersion);

    static final String METADATA_TOPIC_NAME = "topics";

    static final String SCHEMA_TOPIC_NAME = "schemas";

    public TopicServiceImpl(KafkaClusters kafkaClusters, ApplicationsService applicationsService,
            NamingService namingService, CurrentUserService userService, GalapagosTopicConfig topicSettings,
            GalapagosEventManager eventManager, MessagesServiceFactory messagesServiceFactory) {
        this.kafkaClusters = kafkaClusters;
        this.applicationsService = applicationsService;
        this.namingService = namingService;
        this.userService = userService;
        this.topicSettings = topicSettings;
        this.eventManager = eventManager;
        this.messagesService = messagesServiceFactory.getMessagesService(TopicServiceImpl.class);
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
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    messagesService.getMessage("UNKNOWN_APPLICATION_ID", topic.getOwnerApplicationId())));
        }

        KafkaCluster environment = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (environment == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        ApplicationMetadata metadata = applicationsService
                .getApplicationMetadata(environmentId, topic.getOwnerApplicationId()).orElse(null);
        if (metadata == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(messagesService.getMessage(
                    "APPLICATION_NOT_REGISTERED_ON_ENVIRONMENT", topic.getOwnerApplicationId(), environmentId)));
        }

        if (!applicationsService.isUserAuthorizedFor(metadata.getApplicationId())) {
            return CompletableFuture.failedFuture(new IllegalStateException(messagesService
                    .getMessage("CURRENT_USER_IS_NO_OWNER_OF_APPLICATION", metadata.getApplicationId())));
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
                return CompletableFuture.failedFuture(new IllegalStateException(
                        messagesService.getMessage("FOR_COMMAND_SUBSCRIBE_TOPIC_ADD_PRODUCER")));
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
                return CompletableFuture.failedFuture(new IllegalStateException(
                        messagesService.getMessage("FOR_COMMAND_SUBSCRIBE_TOPIC_REMOVE_PRODUCER")));
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
                return CompletableFuture.failedFuture(
                        new IllegalStateException(messagesService.getMessage("CANNOT_CHANGE_OWNER_INTERNAL_TOPICS")));
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
                return CompletableFuture.failedFuture(new IllegalStateException(messagesService
                        .getMessage("CANNOT_UPDATE_SUBSCRIPTION_APPROVAL_REQUIRED_INTERNAL_FLAG_TOPIC")));
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
            return CompletableFuture
                    .failedFuture(new IllegalStateException(messagesService.getMessage("NO_USER_CURRENTLY_LOGGED_IN")));
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
            return CompletableFuture.failedFuture(
                    new IllegalStateException(messagesService.getMessage("NO_SCHEMA_CURRENT_STAGE_TOPIC", topicName)));
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
            return CompletableFuture
                    .failedFuture(new IllegalStateException(messagesService.getMessage("SCHEMA_ALREADY_STAGED")));
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
            return CompletableFuture
                    .failedFuture(new IllegalStateException(messagesService.getMessage("NO_USER_CURRENTLY_LOGGED_IN")));
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
            return CompletableFuture.failedFuture(
                    new IllegalStateException(messagesService.getMessage("CANNOT_ADD_JSON_SCHEMAS_INTERNAL_TOPICS")));
        }

        List<SchemaMetadata> existingVersions = getTopicSchemaVersions(environmentId, topicName);

        Schema newSchema;
        try {
            newSchema = compileSchema(schemaMetadata.getJsonSchema());

        }
        catch (JSONException | SchemaException e) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(messagesService.getMessage("CANNOT_PARSE_JSON_SCHEMA"), e));
        }

        JSONObject json = new JSONObject(schemaMetadata.getJsonSchema());
        if (!json.has("$schema")) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(messagesService.getMessage("JSON_SCHEMA_MUST_DECLARE_SCHEMA_VALUE")));
        }

        if (newSchema.definesProperty("data")
                && (metadata.getType() == TopicType.EVENTS || metadata.getType() == TopicType.COMMANDS)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    messagesService.getMessage("JSON_SCHEMA_MUST_NOT_DECLARE_DATA_OBJECT")));
        }

        if (existingVersions.isEmpty() && schemaMetadata.getSchemaVersion() != 1) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(messagesService
                    .getMessage("ILLEGAL_NEXT_VERSION_NUMBER_TOPIC", schemaMetadata.getSchemaVersion(), topicName)));
        }
        if (!existingVersions.isEmpty() && existingVersions.get(existingVersions.size() - 1)
                .getSchemaVersion() != schemaMetadata.getSchemaVersion() - 1) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(messagesService
                    .getMessage("ILLEGAL_NEXT_VERSION_NUMBER_TOPIC", schemaMetadata.getSchemaVersion(), topicName)));
        }

        SchemaMetadata previousVersion = existingVersions.isEmpty() ? null
                : existingVersions.get(existingVersions.size() - 1);

        if (previousVersion != null && skipCompatCheck == SchemaCompatCheckMode.CHECK_SCHEMA) {
            try {
                Schema previousSchema = compileSchema(previousVersion.getJsonSchema());

                // additional test: if both are equal, do not accept (save a tree!)
                if (SchemaUtil.areEqual(newSchema, previousSchema)) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException(
                            messagesService.getMessage("NEW_SCHEMA_IS_IDENTICAL_TO_THE_LATEST")));
                }

                SchemaCompatibilityValidator validator;
                if (metadata.getType() == TopicType.COMMANDS) {
                    validator = new SchemaCompatibilityValidator(newSchema, previousSchema,
                            new ProducerCompatibilityErrorHandler(
                                    topicSettings.getSchemas().isAllowAddedPropertiesOnCommandTopics()));
                }
                else {
                    validator = new SchemaCompatibilityValidator(previousSchema, newSchema,
                            new ConsumerCompatibilityErrorHandler(
                                    topicSettings.getSchemas().isAllowRemovedOptionalProperties()));
                }

                validator.validate();
            }
            catch (JSONException e) {
                // how, on earth, did it get into the repo then???
                log.error(messagesService.getMessage("INVALID_SCHEMA_IN_REPOSITORY_FOUND_FOR_TOPIC", topicName,
                        environmentId, previousVersion.getSchemaVersion()));

                // danger zone here: allow full replacement of invalid schema (fallthrough)
            }
            catch (IncompatibleSchemaException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        if (existingVersions.isEmpty() && schemaMetadata.getChangeDescription() != null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(messagesService.getMessage("CANNOT_CHANGE_DESCRIPTION_FOR_SCHEMA")));
        }

        if (!existingVersions.isEmpty() && schemaMetadata.getChangeDescription() == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    messagesService.getMessage("CHANGE_DESCRIPTION_REQUIRED_FOR_VERSION_GREATER_THAN_1")));
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
            return CompletableFuture.failedFuture(new IllegalStateException(
                    messagesService.getMessage("CANNOT_RETRIEVE_DATA_INTERNAL_TOPICS_VIA_GALAPAGOS")));
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
                    .failedFuture(new NoSuchElementException(messagesService.getMessage("TOPIC_NOT_FOUND", topicName)));
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

    private <T> CompletableFuture<T> noSuchTopic(String environmentId, String topicName) {
        return CompletableFuture.failedFuture(new NoSuchElementException(
                messagesService.getMessage("NO_TOPIC_WITH_NAME_WAS_FOUND", topicName, environmentId)));
    }

    private interface TopicServiceAction {
        CompletableFuture<Void> apply(KafkaCluster cluster, TopicMetadata topic, GalapagosEventSink eventSink);
    }

}
