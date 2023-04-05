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

/**
 * Singleton Component helping with recurring tasks regarding Kafka ACLs; mainly, calculating required ACLs for given
 * applications and environments.
 */
@Component
public class AclSupport {

    private static final List<AclOperationAndType> READ_TOPIC_OPERATIONS = Arrays.asList(allow(AclOperation.READ),
            allow(AclOperation.DESCRIBE_CONFIGS));

    private static final List<AclOperationAndType> WRITE_TOPIC_OPERATIONS = Arrays.asList(allow(AclOperation.ALL),
            deny(AclOperation.DELETE));

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

        // add configured default ACLs, if any
        if (kafkaConfig.getDefaultAcls() != null) {
            result.addAll(kafkaConfig.getDefaultAcls().stream()
                    .map(acl -> new AclBinding(
                            new ResourcePattern(acl.getResourceType(), acl.getName(), acl.getPatternType()),
                            new AccessControlEntry(kafkaUserName, "*", acl.getOperation(), AclPermissionType.ALLOW)))
                    .collect(Collectors.toList()));
        }

        List<AclOperationAndType> internalTopicOps = readOnly ? READ_TOPIC_OPERATIONS : ALLOW_ALL;
        result.addAll(applicationMetadata.getInternalTopicPrefixes().stream()
                .flatMap(prefix -> prefixAcls(kafkaUserName, ResourceType.TOPIC, prefix, internalTopicOps).stream())
                .collect(Collectors.toList()));

        if (!readOnly) {
            result.addAll(applicationMetadata.getTransactionIdPrefixes().stream().flatMap(
                    prefix -> prefixAcls(kafkaUserName, ResourceType.TRANSACTIONAL_ID, prefix, ALLOW_ALL).stream())
                    .collect(Collectors.toList()));
            result.addAll(applicationMetadata.getConsumerGroupPrefixes().stream()
                    .flatMap(prefix -> prefixAcls(kafkaUserName, ResourceType.GROUP, prefix, ALLOW_ALL).stream())
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

    /**
     * "Simplifies" (reduces) the given set of ACLs. For example, if there are two identical ACLs, one allows ALL for a
     * resource pattern and principal, and one allows READ for the very same resource pattern and principal, the READ
     * ACL can safely be removed.
     *
     * @param aclBindings Set of ACL Bindings to simplify.
     * @return Simplified (potentially identical) set of ACL Bindings.
     */
    public Collection<AclBinding> simplify(Collection<AclBinding> aclBindings) {
        Set<ResourcePatternAndPrincipal> allowedAllPatterns = aclBindings.stream()
                .filter(acl -> acl.entry().permissionType() == AclPermissionType.ALLOW
                        && acl.entry().operation() == AclOperation.ALL)
                .map(acl -> new ResourcePatternAndPrincipal(acl.pattern(), acl.entry().principal()))
                .collect(Collectors.toSet());

        if (allowedAllPatterns.isEmpty()) {
            return aclBindings;
        }

        return aclBindings.stream()
                .filter(acl -> acl.entry().operation() == AclOperation.ALL
                        || acl.entry().permissionType() == AclPermissionType.DENY
                        || !allowedAllPatterns
                                .contains(new ResourcePatternAndPrincipal(acl.pattern(), acl.entry().principal())))
                .collect(Collectors.toSet());
    }

    private Collection<AclBinding> prefixAcls(String userName, ResourceType resourceType, String prefix,
            List<AclOperationAndType> ops) {
        return ops.stream().map(op -> op.toBinding(prefix, resourceType, PatternType.PREFIXED, userName))
                .collect(Collectors.toList());
    }

    private Collection<AclBinding> topicAcls(String userName, String topicName, List<AclOperationAndType> ops) {
        return ops.stream().map(op -> op.toBinding(topicName, ResourceType.TOPIC, PatternType.LITERAL, userName))
                .collect(Collectors.toList());
    }

    private static class AclOperationAndType {

        private final AclOperation operation;

        private final AclPermissionType permissionType;

        private AclOperationAndType(AclOperation operation, AclPermissionType permissionType) {
            this.operation = operation;
            this.permissionType = permissionType;
        }

        public AclBinding toBinding(String resourceName, ResourceType resourceType, PatternType patternType,
                String principal) {
            return new AclBinding(new ResourcePattern(resourceType, resourceName, patternType),
                    new AccessControlEntry(principal, "*", operation, permissionType));
        }
    }

    private static class ResourcePatternAndPrincipal {

        private final ResourcePattern resourcePattern;

        private final String principal;

        private ResourcePatternAndPrincipal(ResourcePattern resourcePattern, String principal) {
            this.resourcePattern = resourcePattern;
            this.principal = principal;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ResourcePatternAndPrincipal that = (ResourcePatternAndPrincipal) o;
            return resourcePattern.equals(that.resourcePattern) && principal.equals(that.principal);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resourcePattern, principal);
        }
    }

    private static AclOperationAndType allow(AclOperation op) {
        return new AclOperationAndType(op, AclPermissionType.ALLOW);
    }

    private static AclOperationAndType deny(AclOperation op) {
        return new AclOperationAndType(op, AclPermissionType.DENY);
    }

    private static final List<AclOperationAndType> ALLOW_ALL = List.of(allow(AclOperation.ALL));

}
