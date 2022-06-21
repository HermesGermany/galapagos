package com.hermesworld.ais.galapagos.kafka.util;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentsConfig;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Singleton Component helping with recurring tasks regarding Kafka ACLs; mainly, calculating required ACLs for given
 * applications and environments.
 */
@Component
public class AclSupport {

    private static final List<AclOperation> READ_TOPIC_OPERATIONS = Arrays.asList(AclOperation.DESCRIBE,
            AclOperation.DESCRIBE_CONFIGS, AclOperation.READ);

    private static final List<AclOperation> WRITE_TOPIC_OPERATIONS = Arrays.asList(AclOperation.DESCRIBE,
            AclOperation.DESCRIBE_CONFIGS, AclOperation.READ, AclOperation.WRITE);

    private final KafkaEnvironmentsConfig kafkaConfig;

    private final TopicService topicService;

    private final SubscriptionService subscriptionService;

    public AclSupport(KafkaEnvironmentsConfig kafkaConfig, TopicService topicService,
            SubscriptionService subscriptionService) {
        this.kafkaConfig = kafkaConfig;
        this.topicService = topicService;
        this.subscriptionService = subscriptionService;
    }

    public Collection<AclBinding> getRequiredAclBindings(String environmentId, ApplicationMetadata applicationMetadata,
            String kafkaUserName, boolean readOnly) {
        Set<AclBinding> result = new HashSet<>();

        String applicationId = applicationMetadata.getApplicationId();

        // every application gets the DESCRIBE CLUSTER right (and also DESCRIBE_CONFIGS, for now)
        result.add(new AclBinding(new ResourcePattern(ResourceType.CLUSTER, "kafka-cluster", PatternType.LITERAL),
                new AccessControlEntry(kafkaUserName, "*", AclOperation.DESCRIBE, AclPermissionType.ALLOW)));
        result.add(new AclBinding(new ResourcePattern(ResourceType.CLUSTER, "kafka-cluster", PatternType.LITERAL),
                new AccessControlEntry(kafkaUserName, "*", AclOperation.DESCRIBE_CONFIGS, AclPermissionType.ALLOW)));

        // add configured default ACLs, if any
        if (kafkaConfig.getDefaultAcls() != null) {
            result.addAll(kafkaConfig.getDefaultAcls().stream()
                    .map(acl -> new AclBinding(
                            new ResourcePattern(acl.getResourceType(), acl.getName(), acl.getPatternType()),
                            new AccessControlEntry(kafkaUserName, "*", acl.getOperation(), AclPermissionType.ALLOW)))
                    .collect(Collectors.toList()));
        }

        List<AclOperation> internalTopicOps = readOnly ? READ_TOPIC_OPERATIONS : List.of(AclOperation.ALL);
        result.addAll(applicationMetadata.getInternalTopicPrefixes().stream()
                .flatMap(prefix -> prefixAcls(kafkaUserName, ResourceType.TOPIC, prefix, internalTopicOps).stream())
                .collect(Collectors.toList()));

        if (!readOnly) {
            result.addAll(applicationMetadata.getTransactionIdPrefixes().stream()
                    .flatMap(prefix -> transactionAcls(kafkaUserName, prefix).stream()).collect(Collectors.toList()));
            result.addAll(applicationMetadata.getConsumerGroupPrefixes().stream().flatMap(
                    prefix -> prefixAcls(kafkaUserName, ResourceType.GROUP, prefix, List.of(AclOperation.ALL)).stream())
                    .collect(Collectors.toList()));
        }

        // topics OWNED by the application
        topicService.listTopics(environmentId).stream().filter(
                topic -> topic.getType() != TopicType.INTERNAL && (applicationId.equals(topic.getOwnerApplicationId())
                        || (topic.getProducers() != null && topic.getProducers().contains(applicationId))))
                .map(topic -> topicAcls(kafkaUserName, topic.getName(),
                        readOnly ? READ_TOPIC_OPERATIONS : WRITE_TOPIC_OPERATIONS))
                .forEach(result::addAll);

        // topics SUBSCRIBED by the application
        subscriptionService.getSubscriptionsOfApplication(environmentId, applicationId, false).stream()
                .map(sub -> topicAcls(kafkaUserName, sub.getTopicName(),
                        topicService.getTopic(environmentId, sub.getTopicName())
                                .map(t -> (t.getType() == TopicType.COMMANDS && !readOnly) ? WRITE_TOPIC_OPERATIONS
                                        : READ_TOPIC_OPERATIONS)
                                .orElse(Collections.emptyList())))
                .forEach(result::addAll);

        return result;
    }

    private Collection<AclBinding> prefixAcls(String userName, ResourceType resourceType, String prefix,
            List<AclOperation> ops) {
        ResourcePattern pattern = new ResourcePattern(resourceType, prefix, PatternType.PREFIXED);
        return ops.stream()
                .map(op -> new AclBinding(pattern, new AccessControlEntry(userName, "*", op, AclPermissionType.ALLOW)))
                .collect(Collectors.toList());
    }

    private Collection<AclBinding> topicAcls(String userName, String topicName, List<AclOperation> ops) {
        ResourcePattern pattern = new ResourcePattern(ResourceType.TOPIC, topicName, PatternType.LITERAL);
        return ops.stream()
                .map(op -> new AclBinding(pattern, new AccessControlEntry(userName, "*", op, AclPermissionType.ALLOW)))
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
