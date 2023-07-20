package com.hermesworld.ais.galapagos.applications.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.events.*;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import com.hermesworld.ais.galapagos.kafka.util.AclSupport;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.thymeleaf.util.StringUtils;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class UpdateApplicationAclsListener
        implements TopicEventsListener, ApplicationEventsListener, SubscriptionEventsListener {

    private final KafkaClusters kafkaClusters;

    private final SubscriptionService subscriptionService;

    private final ApplicationsService applicationsService;

    private final AclSupport aclSupport;

    public UpdateApplicationAclsListener(KafkaClusters kafkaClusters, SubscriptionService subscriptionService,
            ApplicationsService applicationsService, AclSupport aclSupport) {
        this.kafkaClusters = kafkaClusters;
        this.subscriptionService = subscriptionService;
        this.applicationsService = applicationsService;
        this.aclSupport = aclSupport;
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionCreated(SubscriptionEvent event) {
        return applicationsService
                .getApplicationMetadata(getCluster(event).getId(), event.getMetadata().getClientApplicationId())
                .map(metadata -> updateApplicationAcls(getCluster(event), metadata)).orElse(FutureUtil.noop());
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionDeleted(SubscriptionEvent event) {
        // same implementation :-)
        return handleSubscriptionCreated(event);
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionUpdated(SubscriptionEvent event) {
        // same implementation :-)
        return handleSubscriptionCreated(event);
    }

    @Override
    public CompletableFuture<Void> handleApplicationRegistered(ApplicationEvent event) {
        return updateApplicationAcls(getCluster(event), event.getMetadata());
    }

    @Override
    public CompletableFuture<Void> handleApplicationAuthenticationChanged(ApplicationAuthenticationChangeEvent event) {
        // early check here because of the potential removal of ACLs below
        if (shallSkipUpdateAcls(getCluster(event))) {
            return FutureUtil.noop();
        }
        ApplicationMetadata prevMetadata = new ApplicationMetadata(event.getMetadata());
        prevMetadata.setAuthenticationJson(event.getOldAuthentication().toString());

        ApplicationUser newUser = new ApplicationUser(event);
        ApplicationUser prevUser = new ApplicationUser(prevMetadata, getCluster(event).getId());
        if ((newUser.getKafkaUserName() != null && newUser.getKafkaUserName().equals(prevUser.getKafkaUserName()))
                || prevUser.getKafkaUserName() == null) {
            // Cluster implementation will deal about ACL delta
            return updateApplicationAcls(getCluster(event), event.getMetadata());
        }
        else {
            return updateApplicationAcls(getCluster(event), event.getMetadata())
                    .thenCompose(o -> getCluster(event).removeUserAcls(prevUser));
        }
    }

    @Override
    public CompletableFuture<Void> handleApplicationOwnerRequestCreated(ApplicationOwnerRequestEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleApplicationOwnerRequestUpdated(ApplicationOwnerRequestEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleApplicationOwnerRequestCanceled(ApplicationOwnerRequestEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicCreated(TopicCreatedEvent event) {
        // same implementation :-)
        return handleTopicDeleted(event);
    }

    @Override
    public CompletableFuture<Void> handleTopicDeleted(TopicEvent event) {
        if (event.getMetadata().getType() == TopicType.INTERNAL) {
            return FutureUtil.noop();
        }

        return applicationsService
                .getApplicationMetadata(getCluster(event).getId(), event.getMetadata().getOwnerApplicationId())
                .map(metadata -> updateApplicationAcls(getCluster(event), metadata)).orElse(FutureUtil.noop());
    }

    @Override
    public CompletableFuture<Void> handleTopicSubscriptionApprovalRequiredFlagChanged(TopicEvent event) {
        KafkaCluster cluster = getCluster(event);

        Set<String> applicationIds = subscriptionService
                .getSubscriptionsForTopic(cluster.getId(), event.getMetadata().getName(), true).stream()
                .map(SubscriptionMetadata::getClientApplicationId).collect(Collectors.toSet());

        CompletableFuture<Void> result = FutureUtil.noop();
        for (String appId : applicationIds) {
            ApplicationMetadata appMeta = applicationsService.getApplicationMetadata(cluster.getId(), appId)
                    .orElse(null);
            if (appMeta != null) {
                result = result.thenCompose(o -> updateApplicationAcls(cluster, appMeta));
            }
        }

        return result;
    }

    @Override
    public CompletableFuture<Void> handleAddTopicProducer(TopicAddProducerEvent event) {
        KafkaCluster cluster = getCluster(event);
        return applicationsService.getApplicationMetadata(cluster.getId(), event.getProducerApplicationId())
                .map(metadata -> updateApplicationAcls(getCluster(event), metadata)).orElse(FutureUtil.noop());

    }

    @Override
    public CompletableFuture<Void> handleRemoveTopicProducer(TopicRemoveProducerEvent event) {
        return applicationsService.getApplicationMetadata(getCluster(event).getId(), event.getProducerApplicationId())
                .map(metadata -> updateApplicationAcls(getCluster(event), metadata)).orElse(FutureUtil.noop());
    }

    @Override
    public CompletableFuture<Void> handleTopicOwnerChanged(TopicOwnerChangeEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicDescriptionChanged(TopicEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicDeprecated(TopicEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicUndeprecated(TopicEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicSchemaAdded(TopicSchemaAddedEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicSchemaDeleted(TopicSchemaRemovedEvent event) {
        return FutureUtil.noop();
    }

    /**
     * Allows external access to the ACL logic for applications, which is quite complex. Currently known user is the
     * Update Listener of the Dev Certificates (DevUserAclListener).
     *
     * @param metadata      Metadata of the application
     * @param environmentId Environment for which the ACLs are needed.
     *
     * @return A KafkaUser object which can be queried for its ACLs.
     */
    public KafkaUser getApplicationUser(ApplicationMetadata metadata, String environmentId) {
        return new ApplicationUser(metadata, environmentId);
    }

    private KafkaCluster getCluster(AbstractGalapagosEvent event) {
        return event.getContext().getKafkaCluster();
    }

    private boolean shallSkipUpdateAcls(KafkaCluster cluster) {
        return kafkaClusters.getEnvironmentMetadata(cluster.getId()).map(config -> config.isNoUpdateApplicationAcls())
                .orElse(false);
    }

    private CompletableFuture<Void> updateApplicationAcls(KafkaCluster cluster, ApplicationMetadata metadata) {
        if (shallSkipUpdateAcls(cluster)) {
            return FutureUtil.noop();
        }
        return cluster.updateUserAcls(new ApplicationUser(metadata, cluster.getId()));
    }

    private class ApplicationUser implements KafkaUser {

        private final ApplicationMetadata metadata;

        private final String environmentId;

        public ApplicationUser(ApplicationEvent event) {
            this(event.getMetadata(), event.getContext().getKafkaCluster().getId());
        }

        public ApplicationUser(ApplicationMetadata metadata, String environmentId) {
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
                LoggerFactory.getLogger(UpdateApplicationAclsListener.class)
                        .warn("Could not parse authentication JSON of application {}", metadata.getApplicationId(), e);
                return null;
            }
        }

        @Override
        public Collection<AclBinding> getRequiredAclBindings() {
            return aclSupport.getRequiredAclBindings(environmentId, metadata, getKafkaUserName(), false);
        }
    }

}
