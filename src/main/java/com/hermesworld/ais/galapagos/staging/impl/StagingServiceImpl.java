package com.hermesworld.ais.galapagos.staging.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.changes.Change;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.staging.Staging;
import com.hermesworld.ais.galapagos.staging.StagingService;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class StagingServiceImpl implements StagingService {

    private final KafkaClusters kafkaClusters;

    private final ApplicationsService applicationsService;

    private final TopicService topicService;

    private final SubscriptionService subscriptionService;

    public StagingServiceImpl(KafkaClusters kafkaClusters, ApplicationsService applicationsService,
            @Qualifier("nonvalidating") TopicService topicService, SubscriptionService subscriptionService) {
        this.kafkaClusters = kafkaClusters;
        this.applicationsService = applicationsService;
        this.topicService = topicService;
        this.subscriptionService = subscriptionService;
    }

    @Override
    public CompletableFuture<Staging> prepareStaging(String applicationId, String environmentIdFrom,
            List<Change> changesFilter) {
        List<? extends KafkaEnvironmentConfig> environmentMetadata = kafkaClusters.getEnvironmentsMetadata();

        String targetEnvironmentId = null;
        for (int i = 0; i < environmentMetadata.size() - 1; i++) {
            if (environmentIdFrom.equals(environmentMetadata.get(i).getId())) {
                targetEnvironmentId = environmentMetadata.get(i + 1).getId();
            }
        }

        if (targetEnvironmentId == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Cannot perform a staging from environment " + environmentIdFrom + ": No next stage found"));
        }

        if (applicationsService.getApplicationMetadata(targetEnvironmentId, applicationId).isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Please create a API Key for the application on the target environment first"));
        }

        return StagingImpl.build(applicationId, environmentIdFrom, targetEnvironmentId, changesFilter, topicService,
                subscriptionService).thenApply(impl -> impl);
    }

}
