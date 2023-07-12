package com.hermesworld.ais.galapagos.changes.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hermesworld.ais.galapagos.changes.Change;
import com.hermesworld.ais.galapagos.changes.ChangeData;
import com.hermesworld.ais.galapagos.changes.ChangesService;
import com.hermesworld.ais.galapagos.events.*;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.InitPerCluster;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.security.AuditPrincipal;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ChangesServiceImpl
        implements ChangesService, TopicEventsListener, SubscriptionEventsListener, InitPerCluster {

    private final KafkaClusters kafkaClusters;

    public ChangesServiceImpl(KafkaClusters kafkaClusters) {
        this.kafkaClusters = kafkaClusters;
    }

    @Override
    public void init(KafkaCluster cluster) {
        getRepository(cluster).getObjects();
    }

    @Override
    public CompletableFuture<Void> handleTopicCreated(TopicCreatedEvent event) {
        return logChange(ChangeBase.createTopic(event.getMetadata(), event.getTopicCreateParams()), event);
    }

    @Override
    public CompletableFuture<Void> handleTopicDeleted(TopicEvent event) {
        return logChange(ChangeBase.deleteTopic(event.getMetadata().getName(),
                event.getMetadata().getType() == TopicType.INTERNAL), event);
    }

    @Override
    public CompletableFuture<Void> handleTopicDescriptionChanged(TopicEvent event) {
        return logChange(ChangeBase.updateTopicDescription(event.getMetadata().getName(),
                event.getMetadata().getDescription(), event.getMetadata().getType() == TopicType.INTERNAL), event);
    }

    @Override
    public CompletableFuture<Void> handleTopicDeprecated(TopicEvent event) {
        return logChange(ChangeBase.markTopicDeprecated(event.getMetadata().getName(),
                event.getMetadata().getDeprecationText(), event.getMetadata().getEolDate()), event);
    }

    @Override
    public CompletableFuture<Void> handleTopicUndeprecated(TopicEvent event) {
        return logChange(ChangeBase.unmarkTopicDeprecated(event.getMetadata().getName()), event);
    }

    @Override
    public CompletableFuture<Void> handleTopicSubscriptionApprovalRequiredFlagChanged(TopicEvent event) {
        return logChange(ChangeBase.updateTopicSubscriptionApprovalRequiredFlag(event.getMetadata().getName(),
                event.getMetadata().isSubscriptionApprovalRequired()), event);
    }

    @Override
    public CompletableFuture<Void> handleAddTopicProducer(TopicAddProducerEvent event) {
        return logChange(ChangeBase.addTopicProducer(event.getMetadata().getName(), event.getProducerApplicationId()),
                event);
    }

    @Override
    public CompletableFuture<Void> handleRemoveTopicProducer(TopicRemoveProducerEvent event) {
        return logChange(
                ChangeBase.removeTopicProducer(event.getMetadata().getName(), event.getProducerApplicationId()), event);
    }

    @Override
    public CompletableFuture<Void> handleTopicOwnerChanged(TopicOwnerChangeEvent event) {
        return logChange(
                ChangeBase.changeTopicOwner(event.getMetadata().getName(), event.getPreviousOwnerApplicationId()),
                event);
    }

    @Override
    public CompletableFuture<Void> handleTopicSchemaAdded(TopicSchemaAddedEvent event) {
        return logChange(ChangeBase.publishTopicSchemaVersion(event.getMetadata().getName(), event.getNewSchema()),
                event);
    }

    @Override
    public CompletableFuture<Void> handleTopicSchemaDeleted(TopicSchemaRemovedEvent event) {
        return logChange(ChangeBase.deleteTopicSchemaVersion(event.getMetadata().getName()), event);
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionCreated(SubscriptionEvent event) {
        return logChange(ChangeBase.subscribeTopic(event.getMetadata()), event);
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionDeleted(SubscriptionEvent event) {
        return logChange(ChangeBase.unsubscribeTopic(event.getMetadata()), event);
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionUpdated(SubscriptionEvent event) {
        return logChange(ChangeBase.updateSubscription(event.getMetadata()), event);
    }

    @Override
    public List<ChangeData> getChangeLog(String environmentId) {
        return kafkaClusters.getEnvironment(environmentId)
                .map(cluster -> getRepository(cluster).getObjects().stream().sorted().collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    private CompletableFuture<Void> logChange(Change change, AbstractGalapagosEvent event) {
        Optional<AuditPrincipal> principal = event.getContext().getContextValue("principal");
        ChangeData data = toChangeData(change, principal);

        try {
            log.info("CHANGE on environment " + event.getContext().getKafkaCluster().getId() + ": "
                    + JsonUtil.newObjectMapper().writeValueAsString(data));
        }
        catch (JsonProcessingException e) {
            log.error("Could not log change", e);
        }

        return getRepository(event.getContext().getKafkaCluster()).save(data);
    }

    private TopicBasedRepository<ChangeData> getRepository(KafkaCluster kafkaCluster) {
        return kafkaCluster.getRepository("changelog", ChangeData.class);
    }

    private ChangeData toChangeData(Change change, Optional<AuditPrincipal> principal) {
        ChangeData data = new ChangeData();
        data.setId(UUID.randomUUID().toString());
        data.setPrincipal(principal.map(p -> p.getName()).orElse("_SYSTEM"));
        data.setPrincipalFullName(principal.filter(p -> p.getFullName() != null).map(AuditPrincipal::getFullName)
                .orElse(data.getPrincipal()));
        data.setTimestamp(ZonedDateTime.now());
        data.setChange(change);

        return data;
    }

}
