package com.hermesworld.ais.galapagos.devauth.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.applications.impl.UpdateApplicationAclsListener;
import com.hermesworld.ais.galapagos.devauth.DevAuthenticationMetadata;
import com.hermesworld.ais.galapagos.events.*;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.TimeService;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.thymeleaf.util.StringUtils;

import javax.annotation.CheckReturnValue;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class DevUserAclListener implements TopicEventsListener, SubscriptionEventsListener, ApplicationEventsListener {

    private final ApplicationsService applicationsService;

    private final SubscriptionService subscriptionService;

    private final TimeService timeService;

    private final UpdateApplicationAclsListener applicationsAclService;

    private final KafkaClusters kafkaClusters;

    public DevUserAclListener(ApplicationsService applicationsService, SubscriptionService subscriptionService,
            TimeService timeService, UpdateApplicationAclsListener applicationsAclService,
            KafkaClusters kafkaClusters) {
        this.applicationsService = applicationsService;
        this.subscriptionService = subscriptionService;
        this.timeService = timeService;
        this.applicationsAclService = applicationsAclService;
        this.kafkaClusters = kafkaClusters;
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleApplicationRegistered(ApplicationEvent event) {
        KafkaCluster cluster = event.getContext().getKafkaCluster();
        String applicationId = event.getMetadata().getApplicationId();

        return updateAcls(cluster, getValidDevCertificatesForApplication(cluster, applicationId));
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
        return updateAcls(cluster, getValidDevCertificateForUser(cluster, event.getRequest().getUserName()));
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

        return updateAcls(cluster, getValidDevCertificatesForApplication(cluster, applicationId));
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
                result = result
                        .thenCompose(o -> updateAcls(cluster, getValidDevCertificatesForApplication(cluster, appId)));
            }
        }

        return result;
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleAddTopicProducer(TopicAddProducerEvent event) {
        Set<DevAuthenticationMetadata> validDevCertificatesForApplication = new HashSet<>(
                getValidDevCertificatesForApplication(event.getContext().getKafkaCluster(),
                        event.getProducerApplicationId()));

        return updateAcls(event.getContext().getKafkaCluster(), validDevCertificatesForApplication);
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> handleRemoveTopicProducer(TopicRemoveProducerEvent event) {
        return updateAcls(event.getContext().getKafkaCluster(), getValidDevCertificatesForApplication(
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

        return updateAcls(cluster, getValidDevCertificatesForApplication(cluster, applicationId)).thenCompose(o -> {
            CompletableFuture<Void> updateSubscribers = CompletableFuture.completedFuture(null);
            for (String applId : clientApplicationIds) {
                updateSubscribers = updateSubscribers
                        .thenCompose(oo -> updateAcls(cluster, getValidDevCertificatesForApplication(cluster, applId)));
            }
            return updateSubscribers;
        });
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

    @CheckReturnValue
    CompletableFuture<Void> updateAcls(KafkaCluster cluster, Set<DevAuthenticationMetadata> metadatas) {
        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
        for (DevAuthenticationMetadata metadata : metadatas) {
            result = result.thenCompose(
                    o -> cluster.updateUserAcls(new DevAuthenticationKafkaUser(metadata, cluster.getId())));
        }
        return result;
    }

    @CheckReturnValue
    CompletableFuture<Void> removeAcls(KafkaCluster cluster, Set<DevAuthenticationMetadata> metadatas) {
        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
        for (DevAuthenticationMetadata metadata : metadatas) {
            result = result.thenCompose(
                    o -> cluster.removeUserAcls(new DevAuthenticationKafkaUser(metadata, cluster.getId())));
        }
        return result;
    }

    // TODO rename method
    private Set<DevAuthenticationMetadata> getValidDevCertificatesForApplication(KafkaCluster cluster,
            String applicationId) {
        Set<String> userNames = applicationsService.getAllApplicationOwnerRequests().stream()
                .filter(req -> req.getState() == RequestState.APPROVED && applicationId.equals(req.getApplicationId()))
                .map(req -> req.getUserName()).collect(Collectors.toSet());

        return getRepository(cluster).getObjects().stream()
                .filter(dev -> isValid(dev, cluster) && userNames.contains(dev.getUserName()))
                .collect(Collectors.toSet());
    }

    // TODO rename method
    private Set<DevAuthenticationMetadata> getValidDevCertificateForUser(KafkaCluster cluster, String userName) {
        return getRepository(cluster).getObjects().stream()
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

    private TopicBasedRepository<DevAuthenticationMetadata> getRepository(KafkaCluster cluster) {
        return DeveloperAuthenticationServiceImpl.getRepository(cluster);
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
            KafkaAuthenticationModule module = kafkaClusters.getAuthenticationModule(environmentId).orElse(null);
            String authJson = metadata.getAuthenticationJson();

            if (module != null && !StringUtils.isEmpty(authJson)) {
                try {
                    return module.extractKafkaUserName(new JSONObject(authJson));
                }
                catch (JSONException e) {
                    return null;
                }
            }
            return null;
        }

        @Override
        public Collection<AclBinding> getRequiredAclBindings() {
            Set<ApplicationMetadata> applications = getApplicationsOfUser(metadata.getUserName(), environmentId);

            Set<AclBinding> result = new HashSet<>();

            // all topics which the application owns or is subscribed to -> READ
            for (ApplicationMetadata application : applications) {
                applicationsAclService.getApplicationUser(application, environmentId).getRequiredAclBindings().stream()
                        .map(acl -> toOwnAcl(acl)).forEach(result::add);
            }

            // and the mighty CLUSTER_DESCRIBE
            result.add(new AclBinding(new ResourcePattern(ResourceType.CLUSTER, "kafka-cluster", PatternType.LITERAL),
                    new AccessControlEntry(getKafkaUserName(), "*", AclOperation.DESCRIBE_CONFIGS,
                            AclPermissionType.ALLOW)));

            return result;
        }

        private Set<ApplicationMetadata> getApplicationsOfUser(String userName, String environmentId) {
            return applicationsService.getAllApplicationOwnerRequests().stream()
                    .filter(req -> req.getState() == RequestState.APPROVED && userName.equals(req.getUserName()))
                    .map(req -> applicationsService.getApplicationMetadata(environmentId, req.getApplicationId())
                            .orElse(null))
                    .filter(m -> m != null).collect(Collectors.toSet());
        }

        private AclBinding toOwnAcl(AclBinding acl) {
            return new AclBinding(
                    new ResourcePattern(acl.pattern().resourceType(), acl.pattern().name(),
                            acl.pattern().patternType()),
                    new AccessControlEntry(getKafkaUserName(), acl.entry().host(), acl.entry().operation(),
                            acl.entry().permissionType()));
        }

    }
}
