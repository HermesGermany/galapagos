package com.hermesworld.ais.galapagos.devcerts.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.applications.impl.UpdateApplicationAclsListener;
import com.hermesworld.ais.galapagos.devcerts.DevCertificateMetadata;
import com.hermesworld.ais.galapagos.events.*;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
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
import org.springframework.stereotype.Component;

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

    public DevUserAclListener(ApplicationsService applicationsService, SubscriptionService subscriptionService,
            TimeService timeService, UpdateApplicationAclsListener applicationsAclService) {
        this.applicationsService = applicationsService;
        this.subscriptionService = subscriptionService;
        this.timeService = timeService;
        this.applicationsAclService = applicationsAclService;
    }

    @Override
    public CompletableFuture<Void> handleApplicationRegistered(ApplicationEvent event) {
        KafkaCluster cluster = event.getContext().getKafkaCluster();
        String applicationId = event.getMetadata().getApplicationId();

        return updateAcls(cluster, getValidDevCertificatesForApplication(cluster, applicationId));
    }

    @Override
    public CompletableFuture<Void> handleApplicationAuthenticationChanged(ApplicationAuthenticationChangeEvent event) {
        return handleApplicationRegistered(event);
    }

    @Override
    public CompletableFuture<Void> handleApplicationOwnerRequestCreated(ApplicationOwnerRequestEvent event) {
        return handleApplicationOwnerRequestUpdated(event);
    }

    @Override
    public CompletableFuture<Void> handleApplicationOwnerRequestUpdated(ApplicationOwnerRequestEvent event) {
        KafkaCluster cluster = event.getContext().getKafkaCluster();
        return updateAcls(cluster, getValidDevCertificateForUser(cluster, event.getRequest().getUserName()));
    }

    @Override
    public CompletableFuture<Void> handleApplicationOwnerRequestCanceled(ApplicationOwnerRequestEvent event) {
        return handleApplicationOwnerRequestUpdated(event);
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionCreated(SubscriptionEvent event) {
        KafkaCluster cluster = event.getContext().getKafkaCluster();
        String applicationId = event.getMetadata().getClientApplicationId();

        return updateAcls(cluster, getValidDevCertificatesForApplication(cluster, applicationId));
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionDeleted(SubscriptionEvent event) {
        return handleSubscriptionCreated(event);
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionUpdated(SubscriptionEvent event) {
        return handleSubscriptionCreated(event);
    }

    @Override
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
    public CompletableFuture<Void> handleAddTopicProducers(TopicAddProducersEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleRemoveTopicProducer(TopicRemoveProducerEvent event) {
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> handleTopicCreated(TopicCreatedEvent event) {
        return handleTopicDeleted(event);
    }

    @Override
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

    CompletableFuture<Void> updateAcls(KafkaCluster cluster, Set<DevCertificateMetadata> metadatas) {
        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
        for (DevCertificateMetadata metadata : metadatas) {
            result = result.thenCompose(o -> cluster.updateUserAcls(new DevKafkaUser(metadata, cluster.getId())));
        }
        return result;
    }

    CompletableFuture<Void> removeAcls(KafkaCluster cluster, Set<DevCertificateMetadata> metadatas) {
        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
        for (DevCertificateMetadata metadata : metadatas) {
            result = result.thenCompose(o -> cluster.removeUserAcls(new DevKafkaUser(metadata, cluster.getId())));
        }
        return result;
    }

    private Set<DevCertificateMetadata> getValidDevCertificatesForApplication(KafkaCluster cluster,
            String applicationId) {
        Set<String> userNames = applicationsService.getAllApplicationOwnerRequests().stream()
                .filter(req -> req.getState() == RequestState.APPROVED && applicationId.equals(req.getApplicationId()))
                .map(req -> req.getUserName()).collect(Collectors.toSet());

        return getRepository(cluster).getObjects().stream()
                .filter(dev -> isValid(dev) && userNames.contains(dev.getUserName())).collect(Collectors.toSet());
    }

    private Set<DevCertificateMetadata> getValidDevCertificateForUser(KafkaCluster cluster, String userName) {
        return getRepository(cluster).getObjects().stream()
                .filter(dev -> isValid(dev) && userName.equals(dev.getUserName())).collect(Collectors.toSet());
    }

    private boolean isValid(DevCertificateMetadata metadata) {
        return metadata.getExpiryDate() != null
                && metadata.getExpiryDate().isAfter(timeService.getTimestamp().toInstant());
    }

    private TopicBasedRepository<DevCertificateMetadata> getRepository(KafkaCluster cluster) {
        return DeveloperCertificateServiceImpl.getRepository(cluster);
    }

    private class DevKafkaUser implements KafkaUser {

        private final DevCertificateMetadata metadata;

        private final String environmentId;

        public DevKafkaUser(DevCertificateMetadata metadata, String environmentId) {
            this.metadata = metadata;
            this.environmentId = environmentId;
        }

        @Override
        public String getKafkaUserName() {
            return "User:" + metadata.getCertificateDn();
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
