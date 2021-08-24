package com.hermesworld.ais.galapagos.applications.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.events.*;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentsConfig;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thymeleaf.util.StringUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class UpdateApplicationAclsListener
        implements TopicEventsListener, ApplicationEventsListener, SubscriptionEventsListener {

    private final KafkaClusters kafkaClusters;

    private final TopicService topicService;

    private final SubscriptionService subscriptionService;

    private final ApplicationsService applicationsService;

    private final KafkaEnvironmentsConfig kafkaConfig;

    private static final List<AclOperation> READ_TOPIC_OPERATIONS = Arrays.asList(AclOperation.DESCRIBE,
            AclOperation.DESCRIBE_CONFIGS, AclOperation.READ);

    private static final List<AclOperation> WRITE_TOPIC_OPERATIONS = Arrays.asList(AclOperation.DESCRIBE,
            AclOperation.DESCRIBE_CONFIGS, AclOperation.READ, AclOperation.WRITE);

    @Autowired
    public UpdateApplicationAclsListener(KafkaClusters kafkaClusters, TopicService topicService,
            SubscriptionService subscriptionService, ApplicationsService applicationsService,
            KafkaEnvironmentsConfig kafkaConfig) {
        this.kafkaClusters = kafkaClusters;
        this.topicService = topicService;
        this.subscriptionService = subscriptionService;
        this.applicationsService = applicationsService;
        this.kafkaConfig = kafkaConfig;
    }

    @Override
    public CompletableFuture<Void> handleSubscriptionCreated(SubscriptionEvent event) {
        return applicationsService
                .getApplicationMetadata(getCluster(event).getId(), event.getMetadata().getClientApplicationId())
                .map(metadata -> getCluster(event)
                        .updateUserAcls(new ApplicationUser(metadata, getCluster(event).getId())))
                .orElse(FutureUtil.noop());
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
        return getCluster(event).updateUserAcls(new ApplicationUser(event));
    }

    @Override
    public CompletableFuture<Void> handleApplicationAuthenticationChanged(ApplicationAuthenticationChangeEvent event) {
        ApplicationMetadata prevMetadata = new ApplicationMetadata(event.getMetadata());
        prevMetadata.setAuthenticationJson(event.getOldAuthentication().toString());

        ApplicationUser newUser = new ApplicationUser(event);
        ApplicationUser prevUser = new ApplicationUser(prevMetadata, event.getContext().getKafkaCluster().getId());
        if ((newUser.getKafkaUserName() != null && newUser.getKafkaUserName().equals(prevUser.getKafkaUserName()))
                || prevUser.getKafkaUserName() == null) {
            // Cluster implementation will deal about ACL delta
            return getCluster(event).updateUserAcls(newUser);
        }
        else {
            return getCluster(event).updateUserAcls(newUser)
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
                .map(metadata -> getCluster(event)
                        .updateUserAcls(new ApplicationUser(metadata, getCluster(event).getId())))
                .orElse(FutureUtil.noop());
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
                result = result.thenCompose(o -> cluster.updateUserAcls(new ApplicationUser(appMeta, cluster.getId())));
            }
        }

        return result;
    }

    @Override
    public CompletableFuture<Void> handleAddTopicProducer(TopicAddProducersEvent event) {
        KafkaCluster cluster = getCluster(event);
        return applicationsService.getApplicationMetadata(cluster.getId(), event.getProducerApplicationId()).map(
                metadata -> getCluster(event).updateUserAcls(new ApplicationUser(metadata, getCluster(event).getId())))
                .orElse(FutureUtil.noop());

    }

    @Override
    public CompletableFuture<Void> handleRemoveTopicProducer(TopicRemoveProducerEvent event) {
        return applicationsService.getApplicationMetadata(getCluster(event).getId(), event.getProducerApplicationId())
                .map(metadata -> getCluster(event)
                        .updateUserAcls(new ApplicationUser(metadata, getCluster(event).getId())))
                .orElse(FutureUtil.noop());
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
            KafkaAuthenticationModule module = kafkaClusters.getAuthenticationModule(environmentId).orElse(null);
            String authJson = metadata.getAuthenticationJson();

            if (module != null && !StringUtils.isEmpty(authJson)) {
                try {
                    return module.extractKafkaUserName(metadata.getApplicationId(), new JSONObject(authJson));
                }
                catch (JSONException e) {
                    return null;
                }
            }
            return null;
        }

        @Override
        public Collection<AclBinding> getRequiredAclBindings() {
            String userName = getKafkaUserName();
            if (userName == null) {
                return List.of();
            }
            String id = metadata.getApplicationId();

            List<AclBinding> result = new ArrayList<>();

            // every application gets the DESCRIBE CLUSTER right (and also DESCRIBE_CONFIGS, for now)
            result.add(new AclBinding(new ResourcePattern(ResourceType.CLUSTER, "kafka-cluster", PatternType.LITERAL),
                    new AccessControlEntry(userName, "*", AclOperation.DESCRIBE, AclPermissionType.ALLOW)));
            result.add(new AclBinding(new ResourcePattern(ResourceType.CLUSTER, "kafka-cluster", PatternType.LITERAL),
                    new AccessControlEntry(userName, "*", AclOperation.DESCRIBE_CONFIGS, AclPermissionType.ALLOW)));

            // add configured default ACLs, if any
            if (kafkaConfig.getDefaultAcls() != null) {
                result.addAll(kafkaConfig.getDefaultAcls().stream()
                        .map(acl -> new AclBinding(
                                new ResourcePattern(acl.getResourceType(), acl.getName(), acl.getPatternType()),
                                new AccessControlEntry(userName, "*", acl.getOperation(), AclPermissionType.ALLOW)))
                        .collect(Collectors.toList()));
            }

            result.addAll(metadata.getConsumerGroupPrefixes().stream()
                    .map(prefix -> prefixAcl(userName, ResourceType.GROUP, prefix)).collect(Collectors.toList()));
            result.addAll(metadata.getInternalTopicPrefixes().stream()
                    .map(prefix -> prefixAcl(userName, ResourceType.TOPIC, prefix)).collect(Collectors.toList()));

            result.addAll(metadata.getTransactionIdPrefixes().stream()
                    .flatMap(prefix -> transactionAcls(userName, prefix).stream()).collect(Collectors.toList()));

            topicService.listTopics(environmentId).stream()
                    .filter(topic -> topic.getType() != TopicType.INTERNAL && (id.equals(topic.getOwnerApplicationId())
                            || topic.getProducers() != null && topic.getProducers().contains(id)))
                    .map(topic -> topicAcls(userName, topic.getName(), WRITE_TOPIC_OPERATIONS)).forEach(result::addAll);

            subscriptionService.getSubscriptionsOfApplication(environmentId, id, false).stream().map(sub -> topicAcls(
                    userName, sub.getTopicName(),
                    topicService.getTopic(environmentId, sub.getTopicName()).map(
                            t -> t.getType() == TopicType.COMMANDS ? WRITE_TOPIC_OPERATIONS : READ_TOPIC_OPERATIONS)
                            .orElse(Collections.emptyList())))
                    .forEach(result::addAll);

            return result;
        }

        private AclBinding prefixAcl(String userName, ResourceType resourceType, String prefix) {
            ResourcePattern pattern = new ResourcePattern(resourceType, prefix, PatternType.PREFIXED);
            AccessControlEntry entry = new AccessControlEntry(userName, "*", AclOperation.ALL, AclPermissionType.ALLOW);
            return new AclBinding(pattern, entry);
        }

        private Collection<AclBinding> topicAcls(String userName, String topicName, List<AclOperation> ops) {
            ResourcePattern pattern = new ResourcePattern(ResourceType.TOPIC, topicName, PatternType.LITERAL);
            return ops.stream().map(
                    op -> new AclBinding(pattern, new AccessControlEntry(userName, "*", op, AclPermissionType.ALLOW)))
                    .collect(Collectors.toList());
        }

        private Collection<AclBinding> transactionAcls(String userName, String prefix) {
            return Stream.of(AclOperation.DESCRIBE, AclOperation.WRITE)
                    .map(op -> new AclBinding(
                            new ResourcePattern(ResourceType.TRANSACTIONAL_ID, prefix, PatternType.PREFIXED),
                            new AccessControlEntry(userName, "*", op, AclPermissionType.ALLOW)))
                    .collect(Collectors.toSet());
        }
    }

}
