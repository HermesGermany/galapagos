package com.hermesworld.ais.galapagos.subscriptions.controller;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Slf4j
public class SubscriptionsController {

    private final SubscriptionService subscriptionService;

    private final ApplicationsService applicationsService;

    private final TopicService topicService;

    private final KafkaClusters kafkaEnvironments;

    private final Supplier<ResponseStatusException> notFound = () -> new ResponseStatusException(HttpStatus.NOT_FOUND);

    public SubscriptionsController(SubscriptionService subscriptionService, ApplicationsService applicationsService,
            TopicService topicService, KafkaClusters kafkaEnvironments) {
        this.subscriptionService = subscriptionService;
        this.applicationsService = applicationsService;
        this.topicService = topicService;
        this.kafkaEnvironments = kafkaEnvironments;
    }

    @GetMapping(value = "/api/applications/{applicationId}/subscriptions/{environmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SubscriptionDto> getApplicationSubscriptions(@PathVariable String applicationId,
            @PathVariable String environmentId, @RequestParam(defaultValue = "false") boolean includeNonApproved) {
        applicationsService.getKnownApplication(applicationId).orElseThrow(notFound);
        kafkaEnvironments.getEnvironmentMetadata(environmentId).orElseThrow(notFound);
        return subscriptionService.getSubscriptionsOfApplication(environmentId, applicationId, includeNonApproved)
                .stream().map(sub -> toDto(environmentId, sub)).collect(Collectors.toList());
    }

    @PutMapping(value = "/api/applications/{applicationId}/subscriptions/{environmentId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SubscriptionDto createApplicationSubscription(@PathVariable String applicationId,
            @PathVariable String environmentId, @RequestBody CreateSubscriptionDto createData) {
        KafkaEnvironmentConfig environment = kafkaEnvironments.getEnvironmentMetadata(environmentId)
                .orElseThrow(notFound);
        if (!applicationsService.isUserAuthorizedFor(applicationId) || environment.isStagingOnly()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            SubscriptionMetadata subscription = subscriptionService.addSubscription(environmentId,
                    createData.getTopicName(), applicationId, createData.getDescription()).get();
            return toDto(environmentId, subscription);
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
        catch (InterruptedException e) {
            return null;
        }
    }

    @PostMapping(value = "/api/topics/{environmentId}/{topicName}/subscriptions/{subscriptionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void updateApplicationSubscription(@PathVariable String environmentId, @PathVariable String topicName,
            @PathVariable String subscriptionId, @RequestBody UpdateSubscriptionDto updateData)
            throws InterruptedException {
        if (updateData == null || updateData.getNewState() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New state for subscription must be provided");
        }

        // plausi check on topic / subscription combination
        subscriptionService.getSubscriptionsForTopic(environmentId, topicName, true).stream()
                .filter(s -> s.getId().equals(subscriptionId)).findAny().orElseThrow(notFound);
        TopicMetadata topicMetadata = topicService.getTopic(environmentId, topicName).orElseThrow(notFound);

        // user must be authorized for Topic Owner application, not subscribing application!
        if (!applicationsService.isUserAuthorizedFor(topicMetadata.getOwnerApplicationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        // Topic must have flag "subscriptionApprovalRequired", otherwise, update is not allowed!
        if (!topicMetadata.isSubscriptionApprovalRequired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Subscription state cannot be updated for topics which do not require subscription approval");
        }

        try {
            subscriptionService.updateSubscriptionState(environmentId, subscriptionId, updateData.getNewState()).get();
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
    }

    @DeleteMapping(value = "/api/applications/{applicationId}/subscriptions/{environmentId}/{subscriptionId}")
    public void deleteApplicationSubscription(@PathVariable String applicationId, @PathVariable String environmentId,
            @PathVariable String subscriptionId) {
        if (!applicationsService.isUserAuthorizedFor(applicationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        KafkaEnvironmentConfig environmentMeta = kafkaEnvironments.getEnvironmentMetadata(environmentId)
                .orElseThrow(notFound);

        if (environmentMeta.isStagingOnly()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            subscriptionService.deleteSubscription(environmentId, subscriptionId).get();
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
        catch (InterruptedException e) {
            return;
        }
    }

    @GetMapping(value = "/api/topics/{environmentId}/{topicName}/subscriptions", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SubscriptionDto> getTopicSubscriptions(@PathVariable String environmentId,
            @PathVariable String topicName, @RequestParam(defaultValue = "false") boolean includeNonApproved) {
        kafkaEnvironments.getEnvironmentMetadata(environmentId).orElseThrow(notFound);
        topicService.getTopic(environmentId, topicName).orElseThrow(notFound);

        return subscriptionService.getSubscriptionsForTopic(environmentId, topicName, includeNonApproved).stream()
                .map(sub -> toDto(environmentId, sub)).collect(Collectors.toList());
    }

    private SubscriptionDto toDto(String environmentId, SubscriptionMetadata subscription) {
        return new SubscriptionDto(subscription.getId(), subscription.getTopicName(), environmentId,
                subscription.getClientApplicationId(), subscription.getState(), subscription.getDescription());
    }

    private ResponseStatusException handleExecutionException(ExecutionException e) {
        Throwable t = e.getCause();
        if (t instanceof IllegalArgumentException || t instanceof IllegalAccessException) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, t.getMessage());
        }
        if (t instanceof NoSuchElementException) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, t.getMessage());
        }

        log.error("Unhandled exception when processing request", e);
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
