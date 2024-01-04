package com.hermesworld.ais.galapagos.devauth.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.devauth.DevAuthenticationMetadata;
import com.hermesworld.ais.galapagos.events.*;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.util.AclSupport;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.TimeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.acl.AclBinding;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.CheckReturnValue;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class DevUserAclListener implements TopicEventsListener, SubscriptionEventsListener, ApplicationEventsListener {

    private final ApplicationsService applicationsService;

    private final SubscriptionService subscriptionService;

    private final TimeService timeService;

    private final AclSupport aclSupport;

    private final KafkaClusters kafkaClusters;

    public DevUserAclListener(ApplicationsService applicationsService, SubscriptionService subscriptionService,
            TimeService timeService, AclSupport aclSupport, KafkaClusters kafkaClusters) {
        this.applicationsService = applicationsService;
        this.subscriptionService = subscriptionService;
        this.timeService = timeService;
        this.aclSupport = aclSupport;
        this.kafkaClusters = kafkaClusters;
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleApplicationRegistered(ApplicationEvent event) {
        KafkaCluster cluster = event.getContext().getKafkaCluster();
        String applicationId = event.getMetadata().getApplicationId();

        return updateAcls(cluster, getValidDevAuthenticationsForApplication(cluster, applicationId));
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleApplicationAuthenticationChanged(ApplicationAuthenticationChangeEvent event) {
        return handleApplicationRegistered(event);
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleApplicationOwnerRequestCreated(ApplicationOwnerRequestEvent event) {
        return handleApplicationOwnerRequestUpdated(event);
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleApplicationOwnerRequestUpdated(ApplicationOwnerRequestEvent event) {
        KafkaCluster cluster = event.getContext().getKafkaCluster();
        return updateAcls(cluster, getValidDevAuthenticationForUser(cluster, event.getRequest().getUserName()));
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleApplicationOwnerRequestCanceled(ApplicationOwnerRequestEvent event) {
        return handleApplicationOwnerRequestUpdated(event);
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleSubscriptionCreated(SubscriptionEvent event) {
        KafkaCluster cluster = event.getContext().getKafkaCluster();
        String applicationId = event.getMetadata().getClientApplicationId();

        return updateAcls(cluster, getValidDevAuthenticationsForApplication(cluster, applicationId));
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleSubscriptionDeleted(SubscriptionEvent event) {
        return handleSubscriptionCreated(event);
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleSubscriptionUpdated(SubscriptionEvent event) {
        return handleSubscriptionCreated(event);
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleTopicSubscriptionApprovalRequiredFlagChanged(TopicEvent event) {
        KafkaCluster cluster = event.getContext().getKafkaCluster();

        Set<String> applicationIds = subscriptionService
                .getSubscriptionsForTopic(cluster.getId(), event.getMetadata().getName(), true).stream()
                .map(s -> s.getClientApplicationId()).collect(Collectors.toSet());

        CompletableFuture<Void> result = FutureUtil.noop();
        for (String appId : applicationIds) {
            ApplicationMetadata appMeta = applicationsService.getApplicationMetadata(cluster.getId(), appId)
                    .orElse(null);
            if (appMeta != null) {
                result = result.thenCompose(
                        o -> updateAcls(cluster, getValidDevAuthenticationsForApplication(cluster, appId)));
            }
        }

        return result;
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleAddTopicProducer(TopicAddProducerEvent event) {
        Set<DevAuthenticationMetadata> validDevCertificatesForApplication = new HashSet<>(
                getValidDevAuthenticationsForApplication(event.getContext().getKafkaCluster(),
                        event.getProducerApplicationId()));

        return updateAcls(event.getContext().getKafkaCluster(), validDevCertificatesForApplication);
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleRemoveTopicProducer(TopicRemoveProducerEvent event) {
        return updateAcls(event.getContext().getKafkaCluster(), getValidDevAuthenticationsForApplication(
                event.getContext().getKafkaCluster(), event.getProducerApplicationId()));
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleTopicOwnerChanged(TopicOwnerChangeEvent event) {
        return FutureUtil.noop();
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleTopicCreated(TopicCreatedEvent event) {
        return handleTopicDeleted(event);
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleTopicDeleted(TopicEvent event) {
        KafkaCluster cluster = event.getContext().getKafkaCluster();
        String applicationId = event.getMetadata().getOwnerApplicationId();

        Set<String> clientApplicationIds = subscriptionService
                .getSubscriptionsForTopic(cluster.getId(), event.getMetadata().getName(), false).stream()
                .map(s -> s.getClientApplicationId()).collect(Collectors.toSet());
        Set<DevAuthenticationMetadata> allAuthentications = Stream.of(Set.of(applicationId), clientApplicationIds)
                .flatMap(s -> s.stream()).flatMap(id -> getValidDevAuthenticationsForApplication(cluster, id).stream())
                .collect(Collectors.toSet());

        return allAuthentications.isEmpty() ? FutureUtil.noop() : updateAcls(cluster, allAuthentications);
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleTopicDescriptionChanged(TopicEvent event) {
        return FutureUtil.noop();
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleTopicDeprecated(TopicEvent event) {
        return FutureUtil.noop();
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleTopicUndeprecated(TopicEvent event) {
        return FutureUtil.noop();
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleTopicSchemaAdded(TopicSchemaAddedEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicSchemaDeleted(TopicSchemaRemovedEvent event) {
        return FutureUtil.noop();
    }

    @CheckReturnValue
    CompletableFuture<Void> updateAcls(KafkaCluster cluster, Set<DevAuthenticationMetadata> metadatas) {
        if (log.isDebugEnabled()) {
            log.debug("Updating ACLs for {} on cluster {}", metadatas.stream().map(m -> m.getUserName()).toList(),
                    cluster.getId());
        }
        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
        for (DevAuthenticationMetadata metadata : metadatas) {
            result = result.thenCompose(
                    o -> cluster.updateUserAcls(new DevAuthenticationKafkaUser(metadata, cluster.getId())));
        }
        return result;
    }

    @CheckReturnValue
    CompletableFuture<Void> removeAcls(KafkaCluster cluster, Set<DevAuthenticationMetadata> metadatas) {
        if (log.isDebugEnabled()) {
            log.debug("Removing ACLs for {} on cluster {}",
                    metadatas.stream().map(m -> m.getUserName()).collect(Collectors.toList()), cluster.getId());
        }
        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
        for (DevAuthenticationMetadata metadata : metadatas) {
            result = result.thenCompose(
                    o -> cluster.removeUserAcls(new DevAuthenticationKafkaUser(metadata, cluster.getId())));
        }
        return result;
    }

    private Set<DevAuthenticationMetadata> getValidDevAuthenticationsForApplication(KafkaCluster cluster,
            String applicationId) {
        Set<String> userNames = applicationsService.getAllApplicationOwnerRequests().stream()
                .filter(req -> req.getState() == RequestState.APPROVED && applicationId.equals(req.getApplicationId()))
                .map(req -> req.getUserName()).collect(Collectors.toSet());

        return DeveloperAuthenticationServiceImpl.getRepository(cluster).getObjects().stream()
                .filter(dev -> isValid(dev, cluster) && userNames.contains(dev.getUserName()))
                .collect(Collectors.toSet());
    }

    private Set<DevAuthenticationMetadata> getValidDevAuthenticationForUser(KafkaCluster cluster, String userName) {
        return DeveloperAuthenticationServiceImpl.getRepository(cluster).getObjects().stream()
                .filter(dev -> isValid(dev, cluster) && userName.equals(dev.getUserName())).collect(Collectors.toSet());
    }

    private boolean isValid(DevAuthenticationMetadata metadata, KafkaCluster cluster) {
        JSONObject json = new JSONObject(metadata.getAuthenticationJson());
        KafkaAuthenticationModule authModule = kafkaClusters.getAuthenticationModule(cluster.getId()).orElse(null);

        if (authModule == null) {
            return false;
        }
        return authModule.extractExpiryDate(json).isPresent()
                && authModule.extractExpiryDate(json).get().isAfter(timeService.getTimestamp().toInstant());

    }

    private class DevAuthenticationKafkaUser implements KafkaUser {

        private final DevAuthenticationMetadata metadata;

        private final String environmentId;

        public DevAuthenticationKafkaUser(DevAuthenticationMetadata metadata, String environmentId) {
            this.metadata = metadata;
            this.environmentId = environmentId;
        }

        @Override
        public String getKafkaUserName() {
            JSONObject authData;
            try {
                authData = new JSONObject(metadata.getAuthenticationJson());
                return kafkaClusters.getAuthenticationModule(environmentId).map(m -> m.extractKafkaUserName(authData))
                        .orElse(null);
            }
            catch (JSONException e) {
                LoggerFactory.getLogger(DevUserAclListener.class).warn(
                        "Could not parse authentication JSON of developer authentication for user {}",
                        metadata.getUserName(), e);
                return null;
            }
        }

        @Override
        public Collection<AclBinding> getRequiredAclBindings() {
            boolean writeAccess = kafkaClusters.getEnvironmentMetadata(environmentId)
                    .map(m -> m.isDeveloperWriteAccess()).orElse(false);

            return aclSupport.simplify(getApplicationsOfUser(metadata.getUserName(), environmentId).stream()
                    .map(a -> aclSupport.getRequiredAclBindings(environmentId, a, getKafkaUserName(), !writeAccess))
                    .flatMap(c -> c.stream()).collect(Collectors.toSet()));
        }

        private Set<ApplicationMetadata> getApplicationsOfUser(String userName, String environmentId) {
            return applicationsService.getAllApplicationOwnerRequests().stream()
                    .filter(req -> req.getState() == RequestState.APPROVED && userName.equals(req.getUserName()))
                    .map(req -> applicationsService.getApplicationMetadata(environmentId, req.getApplicationId())
                            .orElse(null))
                    .filter(m -> m != null).collect(Collectors.toSet());
        }

    }
}
