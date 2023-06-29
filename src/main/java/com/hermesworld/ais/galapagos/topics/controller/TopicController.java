package com.hermesworld.ais.galapagos.topics.controller;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.BusinessCapability;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.TopicConfigEntry;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.naming.InvalidTopicNameException;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.schemas.IncompatibleSchemaException;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.topics.SchemaCompatCheckMode;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.ValidatingTopicService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.header.Header;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestController
@Slf4j
public class TopicController {

    private final ValidatingTopicService topicService;

    private final KafkaClusters kafkaEnvironments;

    private final ApplicationsService applicationsService;

    private final NamingService namingService;

    private final CurrentUserService userService;

    private static final Supplier<ResponseStatusException> badRequest = () -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST);

    private static final Supplier<ResponseStatusException> notFound = () -> new ResponseStatusException(
            HttpStatus.NOT_FOUND);

    private static final int PEEK_LIMIT = 100;

    public TopicController(ValidatingTopicService topicService, KafkaClusters kafkaEnvironments,
            ApplicationsService applicationsService, NamingService namingService, CurrentUserService userService) {
        this.topicService = topicService;
        this.kafkaEnvironments = kafkaEnvironments;
        this.applicationsService = applicationsService;
        this.namingService = namingService;
        this.userService = userService;
    }

    @GetMapping(value = "/api/topics/{environmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TopicDto> listTopics(@PathVariable String environmentId,
            @RequestParam(required = false, defaultValue = "true") boolean includeInternal) {
        kafkaEnvironments.getEnvironmentMetadata(environmentId).orElseThrow(notFound);

        List<String> userAppIds = !includeInternal ? Collections.emptyList()
                : applicationsService.getUserApplications().stream().map(KnownApplication::getId)
                        .collect(Collectors.toList());

        return topicService.listTopics(environmentId).stream()
                .filter(t -> t.getType() != TopicType.INTERNAL || userAppIds.contains(t.getOwnerApplicationId()))
                .map(t -> toDto(environmentId, t, topicService.canDeleteTopic(environmentId, t.getName())))
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/api/topicconfigs/{environmentId}/{topicName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TopicConfigEntryDto> getTopicConfig(@PathVariable String environmentId,
            @PathVariable String topicName) {
        KafkaCluster cluster = kafkaEnvironments.getEnvironment(environmentId).orElseThrow(notFound);
        topicService.listTopics(environmentId).stream().filter(topic -> topicName.equals(topic.getName())).findAny()
                .orElseThrow(notFound);

        try {
            return cluster.getTopicConfig(topicName)
                    .thenApply(set -> set.stream().map(this::toConfigEntryDto).collect(Collectors.toList())).get();
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
        catch (InterruptedException e) {
            return null;
        }
    }

    @PostMapping(value = "/api/producers/{environmentId}/{topicName}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void addProducerToTopic(@PathVariable String environmentId, @PathVariable String topicName,
            @RequestBody AddProducerDto producer) {
        TopicMetadata topic = topicService.getTopic(environmentId, topicName).orElseThrow(notFound);

        if (!applicationsService.isUserAuthorizedFor(topic.getOwnerApplicationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (StringUtils.isEmpty(producer.getProducerApplicationId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        try {
            topicService.addTopicProducer(environmentId, topicName, producer.getProducerApplicationId()).get();
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @DeleteMapping(value = "/api/producers/{envId}/{topicName}/{producerApplicationId}")
    public ResponseEntity<Void> removeProducerFromTopic(@PathVariable String envId, @PathVariable String topicName,
            @PathVariable String producerApplicationId) {
        if (envId.isEmpty() || topicName.isEmpty() || producerApplicationId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        TopicMetadata topic = topicService.getTopic(envId, topicName).orElseThrow(notFound);
        if (!applicationsService.isUserAuthorizedFor(topic.getOwnerApplicationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            topicService.removeTopicProducer(envId, topicName, producerApplicationId).get();
            return ResponseEntity.noContent().build();

        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
        catch (InterruptedException e) {
            return null;
        }
    }

    @PostMapping(value = "/api/change-owner/{envId}/{topicName}")
    public void changeTopicOwner(@PathVariable String envId, @PathVariable String topicName,
            @RequestBody @Valid ChangeTopicOwnerDto request) {
        if (envId.isEmpty() || topicName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        TopicMetadata topic = topicService.getTopic(envId, topicName).orElseThrow(notFound);
        if (!applicationsService.isUserAuthorizedFor(topic.getOwnerApplicationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            topicService.changeTopicOwner(envId, topicName, request.getProducerApplicationId()).get();
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }

    @PostMapping(value = "/api/topics/{environmentId}/{topicName}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void updateTopic(@PathVariable String environmentId, @PathVariable String topicName,
            @RequestBody UpdateTopicDto request) {

        TopicMetadata topic = topicService.getTopic(environmentId, topicName).orElseThrow(notFound);
        if (!applicationsService.isUserAuthorizedFor(topic.getOwnerApplicationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        try {

            if (request.isUpdateDescription()) {
                topicService.updateTopicDescription(environmentId, topicName, request.getDescription()).get();
                return;
            }

            if (!StringUtils.isEmpty(request.getDeprecationText())) {
                if (request.getEolDate() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "eolDate must be set for Topic deprecation");
                }

                topicService.markTopicDeprecated(topicName, request.getDeprecationText(), request.getEolDate()).get();

            }
            else {
                if (!topic.isDeprecated()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Cannot remove deprecation from a topic that was not deprecated");
                }
                topicService.unmarkTopicDeprecated(topicName).get();
            }
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PostMapping(value = "/api/topicconfigs/{environmentId}/{topicName}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public void updateTopicConfig(@PathVariable String environmentId, @PathVariable String topicName,
            @RequestBody List<UpdateTopicConfigEntryDto> configs) throws InterruptedException {
        KafkaCluster cluster = kafkaEnvironments.getEnvironment(environmentId).orElseThrow(notFound);
        TopicMetadata metadata = topicService.listTopics(environmentId).stream()
                .filter(topic -> topicName.equals(topic.getName())).findAny().orElseThrow(notFound);

        if (!applicationsService.isUserAuthorizedFor(metadata.getOwnerApplicationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        for (UpdateTopicConfigEntryDto config : configs) {
            if (StringUtils.isEmpty(config.getName()) || StringUtils.isEmpty(config.getValue())) {
                throw badRequest.get();
            }
        }

        try {
            cluster.setTopicConfig(topicName,
                    configs.stream().collect(
                            Collectors.toMap(UpdateTopicConfigEntryDto::getName, UpdateTopicConfigEntryDto::getValue)))
                    .get();
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
    }

    @PostMapping(value = "/api/util/topicname", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TopicNameDto getTopicNameSuggestion(@RequestBody TopicNameSuggestionQueryDto query) {
        if (StringUtils.isEmpty(query.getApplicationId()) || StringUtils.isEmpty(query.getEnvironmentId())
                || query.getTopicType() == null) {
            throw badRequest.get();
        }

        // TODO should go into TopicService
        KnownApplication app = applicationsService.getKnownApplication(query.getApplicationId())
                .orElseThrow(badRequest);
        BusinessCapability cap = app.getBusinessCapabilities().stream()
                .filter(bc -> bc.getId().equals(query.getBusinessCapabilityId())).findFirst().orElse(null);

        ApplicationMetadata metadata = applicationsService
                .getApplicationMetadata(query.getEnvironmentId(), query.getApplicationId()).orElse(null);
        if (metadata == null) {
            throw badRequest.get();
        }

        String name = namingService.getTopicNameSuggestion(query.getTopicType(), app, cap);
        if (name == null) {
            throw badRequest.get();
        }

        return new TopicNameDto(name);
    }

    @PutMapping(value = "/api/topics/{environmentId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TopicDto createTopic(@PathVariable String environmentId, @RequestBody CreateTopicDto topicData) {
        if (!applicationsService.isUserAuthorizedFor(topicData.getOwnerApplicationId())) {
            // TODO Security Audit log?
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        KafkaEnvironmentConfig envMeta = kafkaEnvironments.getEnvironmentMetadata(environmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (envMeta.isStagingOnly()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (topicData.getTopicType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing topic type");
        }

        if (StringUtils.isEmpty(topicData.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing topic name");
        }

        try {
            return toDto(environmentId,
                    topicService
                            .createTopic(environmentId, toMetadata(topicData), topicData.getPartitionCount(),
                                    Optional.ofNullable(topicData.getTopicConfig()).orElse(Collections.emptyMap()))
                            .get(),
                    true);
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
        catch (InterruptedException e) {
            return null;
        }
    }

    @DeleteMapping(value = "/api/topics/{environmentId}/{topicName}")
    public ResponseEntity<Void> deleteTopic(@PathVariable String environmentId, @PathVariable String topicName) {
        TopicMetadata metadata = topicService.listTopics(environmentId).stream()
                .filter(topic -> topicName.equals(topic.getName())).findAny().orElseThrow(notFound);

        kafkaEnvironments.getEnvironmentMetadata(environmentId).orElseThrow(notFound);

        if (!applicationsService.isUserAuthorizedFor(metadata.getOwnerApplicationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (!topicService.canDeleteTopic(environmentId, topicName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            topicService.deleteTopic(environmentId, topicName).get();
        }
        catch (InterruptedException e) {
            return null;
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }

        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/api/schemas/{environmentId}/{topicName}")
    public List<SchemaMetadata> getTopicSchemas(@PathVariable String environmentId, @PathVariable String topicName) {
        if (topicService.getTopic(environmentId, topicName).isEmpty()) {
            throw notFound.get();
        }

        return topicService.getTopicSchemaVersions(environmentId, topicName);
    }

    // intentionally no /api - unprotected resource!
    @GetMapping(value = "/schema/{schemaId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getSchema(@PathVariable String schemaId) {
        if ("empty".equals(schemaId)) {
            return "{}";
        }

        for (String id : kafkaEnvironments.getEnvironmentIds()) {
            Optional<SchemaMetadata> schema = topicService.getSchemaById(id, schemaId);
            if (schema.isPresent()) {
                return schema.get().getJsonSchema();
            }
        }

        throw notFound.get();
    }

    @PutMapping(value = "/api/schemas/{environmentId}/{topicName}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addTopicSchemaVersion(@PathVariable String environmentId,
            @PathVariable String topicName, @RequestParam(defaultValue = "false") boolean skipCompatCheck,
            @RequestBody AddSchemaVersionDto schemaVersionDto) {
        TopicMetadata topic = topicService.listTopics(environmentId).stream().filter(t -> topicName.equals(t.getName()))
                .findAny().orElseThrow(notFound);
        if (!applicationsService.isUserAuthorizedFor(topic.getOwnerApplicationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (schemaVersionDto == null || StringUtils.isEmpty(schemaVersionDto.getJsonSchema())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "JSON Schema (jsonSchema property) is missing from request body");
        }
        SchemaCompatCheckMode checkMode = skipCompatCheck ? SchemaCompatCheckMode.SKIP_SCHEMA_CHECK
                : SchemaCompatCheckMode.CHECK_SCHEMA;

        if (skipCompatCheck && !userService.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            SchemaMetadata metadata = topicService.addTopicSchemaVersion(environmentId, topicName,
                    schemaVersionDto.getJsonSchema(), schemaVersionDto.getChangeDescription(), checkMode).get();

            return ResponseEntity.created(new URI("/schema/" + metadata.getId())).build();
        }
        catch (InterruptedException e) {
            return null;
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
        catch (URISyntaxException e) {
            // should not occur for /schema/ + UUID
            throw new RuntimeException(e);
        }
    }

    @DeleteMapping(value = "/api/schemas/{environmentId}/{topicName}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteLatestTopicSchemaVersion(@PathVariable String environmentId,
            @PathVariable String topicName) {

        TopicMetadata topic = topicService.listTopics(environmentId).stream().filter(t -> topicName.equals(t.getName()))
                .findAny().orElseThrow(notFound);
        if (!applicationsService.isUserAuthorizedFor(topic.getOwnerApplicationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            topicService.deleteLatestTopicSchemaVersion(environmentId, topicName).get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/util/peek-data/{environmentId}/{topicName}")
    public List<ConsumerRecordDto> peekTopicData(@PathVariable String environmentId, @PathVariable String topicName) {
        try {
            return topicService.peekTopicData(environmentId, topicName, PEEK_LIMIT).get().stream()
                    .map(this::toRecordDto).collect(Collectors.toList());
        }
        catch (InterruptedException e) {
            return Collections.emptyList();
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
    }

    private TopicDto toDto(String environmentId, TopicMetadata topic, boolean canDelete) {
        return new TopicDto(topic.getName(), topic.getType().toString(), environmentId, topic.getDescription(),
                topic.getInfoUrl(), topic.getOwnerApplicationId(), topic.isDeprecated(), topic.getDeprecationText(),
                topic.getEolDate() == null ? null : topic.getEolDate().toString(),
                topic.isSubscriptionApprovalRequired(), canDelete, topic.getCompactionTimeMillis(),
                topic.getRetentionTimeMillis(), topic.getCriticality(), topic.getMessagesPerDay(),
                topic.getMessagesSize(), topic.getProducers());
    }

    private TopicConfigEntryDto toConfigEntryDto(TopicConfigEntry configEntry) {
        return new TopicConfigEntryDto(configEntry.getName(), configEntry.getValue(), configEntry.isDefault(),
                configEntry.isReadOnly(), configEntry.isSensitive());
    }

    private TopicMetadata toMetadata(CreateTopicDto dto) {
        TopicMetadata topic = new TopicMetadata();
        topic.setName(dto.getName());
        topic.setDescription(dto.getDescription());
        topic.setOwnerApplicationId(dto.getOwnerApplicationId());
        topic.setType(dto.getTopicType());
        topic.setSubscriptionApprovalRequired(dto.isSubscriptionApprovalRequired());
        topic.setCompactionTimeMillis(dto.getCompactionTimeMillis());
        topic.setRetentionTimeMillis(dto.getRetentionTimeMillis());
        topic.setCriticality(dto.getCriticality());
        topic.setMessagesPerDay(dto.getMessagesPerDay());
        topic.setMessagesSize(dto.getMessagesSize());

        return topic;
    }

    private ConsumerRecordDto toRecordDto(ConsumerRecord<String, String> record) {
        Map<String, String> headers = StreamSupport.stream(record.headers().spliterator(), false)
                .collect(Collectors.toMap(Header::key, h -> new String(h.value(), StandardCharsets.UTF_8)));
        return new ConsumerRecordDto(record.key(), record.value(), record.offset(), record.timestamp(),
                record.partition(), headers);
    }

    private ResponseStatusException handleExecutionException(ExecutionException e) {
        Throwable t = e.getCause();
        if (t instanceof IllegalArgumentException || t instanceof IllegalStateException
                || t instanceof InvalidTopicNameException || t instanceof IncompatibleSchemaException) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (t instanceof KafkaException) {
            log.error("Unexpected Kafka exception during handling Topic REST call", t);
            return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        if (t instanceof NoSuchElementException) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        log.error("Unexpected exception during request handling: ", t);
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
