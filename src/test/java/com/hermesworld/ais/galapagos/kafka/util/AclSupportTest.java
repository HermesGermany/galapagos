package com.hermesworld.ais.galapagos.kafka.util;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.kafka.config.DefaultAclConfig;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentsConfig;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AclSupportTest {

    private static final List<AclOperationAndType> WRITE_TOPIC_OPERATIONS = Arrays.asList(
            new AclOperationAndType(AclOperation.ALL, AclPermissionType.ALLOW),
            new AclOperationAndType(AclOperation.DELETE, AclPermissionType.DENY));

    private static final List<AclOperationAndType> READ_TOPIC_OPERATIONS = Arrays.asList(
            new AclOperationAndType(AclOperation.READ, AclPermissionType.ALLOW),
            new AclOperationAndType(AclOperation.DESCRIBE_CONFIGS, AclPermissionType.ALLOW));

    @Mock
    private KafkaEnvironmentsConfig kafkaConfig;

    @Mock
    private TopicService topicService;

    @Mock
    private SubscriptionService subscriptionService;

    @BeforeEach
    void initMocks() {

    }

    @Test
    void testGetRequiredAclBindings_simple() {
        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("app01");
        metadata.setConsumerGroupPrefixes(List.of("group.myapp.", "group2.myapp."));
        metadata.setInternalTopicPrefixes(List.of("de.myapp.", "de.myapp2."));
        metadata.setTransactionIdPrefixes(List.of("de.myapp."));

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic1");
        topic1.setType(TopicType.EVENTS);
        topic1.setOwnerApplicationId("app01");
        TopicMetadata topic2 = new TopicMetadata();
        topic2.setName("topic2");
        topic2.setType(TopicType.EVENTS);
        topic2.setOwnerApplicationId("app02");
        TopicMetadata topic3 = new TopicMetadata();
        topic3.setName("topic3");
        topic3.setType(TopicType.EVENTS);
        topic3.setOwnerApplicationId("app02");

        SubscriptionMetadata sub = new SubscriptionMetadata();
        sub.setId("1");
        sub.setClientApplicationId("app01");
        sub.setTopicName("topic2");

        when(topicService.listTopics("_test")).thenReturn(List.of(topic1, topic2));
        when(topicService.getTopic("_test", "topic2")).thenReturn(Optional.of(topic2));
        when(subscriptionService.getSubscriptionsOfApplication("_test", "app01", false)).thenReturn(List.of(sub));

        AclSupport aclSupport = new AclSupport(kafkaConfig, topicService, subscriptionService);

        Collection<AclBinding> acls = aclSupport.getRequiredAclBindings("_test", metadata, "User:CN=testapp", false);

        assertEquals(9, acls.size());

        // two ACL for groups and two for topic prefixes must have been created
        assertNotNull(acls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                        && binding.pattern().patternType() == PatternType.PREFIXED
                        && binding.pattern().name().equals("de.myapp.")
                        && binding.entry().operation().equals(AclOperation.ALL))
                .findAny().orElse(null));
        assertNotNull(acls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                        && binding.pattern().patternType() == PatternType.PREFIXED
                        && binding.pattern().name().equals("de.myapp2.")
                        && binding.entry().operation().equals(AclOperation.ALL))
                .findAny().orElse(null));
        assertNotNull(acls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.GROUP
                        && binding.pattern().patternType() == PatternType.PREFIXED
                        && binding.pattern().name().equals("group.myapp."))
                .findAny().orElse(null));
        assertNotNull(acls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.GROUP
                        && binding.pattern().patternType() == PatternType.PREFIXED
                        && binding.pattern().name().equals("group2.myapp."))
                .findAny().orElse(null));

        // transaction ACLs must have been created
        assertNotNull(acls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.TRANSACTIONAL_ID
                        && binding.pattern().patternType() == PatternType.PREFIXED
                        && binding.pattern().name().equals("de.myapp.")
                        && binding.entry().operation() == AclOperation.ALL)
                .findAny().orElse(null));

        // Write rights for owned topic must also be present
        WRITE_TOPIC_OPERATIONS.forEach(op -> assertNotNull(acls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                        && binding.pattern().patternType() == PatternType.LITERAL
                        && binding.pattern().name().equals("topic1") && binding.entry().operation() == op.operation
                        && binding.entry().permissionType() == op.permissionType)));

        // and read rights for subscribed topic
        READ_TOPIC_OPERATIONS.forEach(op -> assertNotNull(acls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                        && binding.pattern().patternType() == PatternType.LITERAL
                        && binding.pattern().name().equals("topic2") && binding.entry().operation() == op.operation
                        && binding.entry().permissionType() == op.permissionType)));
    }

    @Test
    void testNoWriteAclsForInternalTopics() {
        ApplicationMetadata app1 = new ApplicationMetadata();
        app1.setApplicationId("app-1");
        app1.setConsumerGroupPrefixes(List.of("groups."));

        TopicMetadata topic = new TopicMetadata();
        topic.setName("int1");
        topic.setType(TopicType.INTERNAL);
        topic.setOwnerApplicationId("app-1");

        when(topicService.listTopics("_test")).thenReturn(List.of(topic));

        AclSupport aclSupport = new AclSupport(kafkaConfig, topicService, subscriptionService);
        Collection<AclBinding> bindings = aclSupport.getRequiredAclBindings("_test", app1, "User:CN=testapp", false);

        assertEquals(1, bindings.size());
        assertFalse(bindings.stream().anyMatch(binding -> binding.pattern().resourceType() == ResourceType.TOPIC));
    }

    @Test
    void testAdditionalProducerWriteAccess() {
        ApplicationMetadata app1 = new ApplicationMetadata();
        app1.setApplicationId("app-1");

        ApplicationMetadata producer1 = new ApplicationMetadata();
        producer1.setApplicationId("producer1");

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic1");
        topic.setType(TopicType.EVENTS);
        topic.setOwnerApplicationId("app-1");
        topic.setProducers(List.of("producer1"));

        when(topicService.listTopics("_test")).thenReturn(List.of(topic));

        AclSupport aclSupport = new AclSupport(kafkaConfig, topicService, subscriptionService);
        Collection<AclBinding> bindings = aclSupport.getRequiredAclBindings("_test", producer1, "User:CN=producer1",
                false);

        WRITE_TOPIC_OPERATIONS.forEach(op -> assertNotNull(
                bindings.stream().filter(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                        && binding.pattern().patternType() == PatternType.LITERAL
                        && binding.pattern().name().equals("topic1") && binding.entry().operation() == op.operation
                        && binding.entry().permissionType() == op.permissionType
                        && binding.entry().principal().equals("User:CN=producer1")).findAny().orElse(null),
                "Did not find expected write ACL for topic (operation " + op.operation.name() + " is missing)"));
    }

    @Test
    void testDefaultAcls() {
        ApplicationMetadata app1 = new ApplicationMetadata();
        app1.setApplicationId("app-1");
        app1.setAuthenticationJson(new JSONObject(Map.of("dn", "CN=testapp")).toString());
        app1.setConsumerGroupPrefixes(List.of("groups."));

        List<DefaultAclConfig> defaultAcls = new ArrayList<>();
        defaultAcls.add(defaultAclConfig("test-group", ResourceType.GROUP, PatternType.PREFIXED, AclOperation.READ));
        defaultAcls.add(defaultAclConfig("test-topic", ResourceType.TOPIC, PatternType.LITERAL, AclOperation.CREATE));
        when(kafkaConfig.getDefaultAcls()).thenReturn(defaultAcls);

        AclSupport aclSupport = new AclSupport(kafkaConfig, topicService, subscriptionService);

        Collection<AclBinding> bindings = aclSupport.getRequiredAclBindings("_test", app1, "User:CN=testapp", false);

        assertTrue(bindings.stream()
                .anyMatch(b -> b.pattern().patternType() == PatternType.PREFIXED
                        && b.pattern().name().equals("test-group") && b.pattern().resourceType() == ResourceType.GROUP
                        && b.entry().operation() == AclOperation.READ
                        && b.entry().permissionType() == AclPermissionType.ALLOW && b.entry().host().equals("*")));
        assertTrue(bindings.stream()
                .anyMatch(b -> b.pattern().patternType() == PatternType.LITERAL
                        && b.pattern().name().equals("test-topic") && b.pattern().resourceType() == ResourceType.TOPIC
                        && b.entry().operation() == AclOperation.CREATE
                        && b.entry().permissionType() == AclPermissionType.ALLOW && b.entry().host().equals("*")));
    }

    @Test
    void testReadOnlyAcls() {
        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("app01");
        metadata.setConsumerGroupPrefixes(List.of("group.myapp.", "group2.myapp."));
        metadata.setInternalTopicPrefixes(List.of("de.myapp.", "de.myapp2."));
        metadata.setTransactionIdPrefixes(List.of("de.myapp."));

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic1");
        topic1.setType(TopicType.EVENTS);
        topic1.setOwnerApplicationId("app01");
        TopicMetadata topic2 = new TopicMetadata();
        topic2.setName("topic2");
        topic2.setType(TopicType.EVENTS);
        topic2.setOwnerApplicationId("app02");
        TopicMetadata topic3 = new TopicMetadata();
        topic3.setName("topic3");
        topic3.setType(TopicType.EVENTS);
        topic3.setOwnerApplicationId("app02");

        SubscriptionMetadata sub = new SubscriptionMetadata();
        sub.setId("1");
        sub.setClientApplicationId("app01");
        sub.setTopicName("topic2");

        when(topicService.listTopics("_test")).thenReturn(List.of(topic1, topic2));
        when(topicService.getTopic("_test", "topic2")).thenReturn(Optional.of(topic2));
        when(subscriptionService.getSubscriptionsOfApplication("_test", "app01", false)).thenReturn(List.of(sub));

        AclSupport aclSupport = new AclSupport(kafkaConfig, topicService, subscriptionService);

        Collection<AclBinding> acls = aclSupport.getRequiredAclBindings("_test", metadata, "User:CN=testapp", true);

        assertEquals(8, acls.size());

        // NO group ACLs must have been created
        assertEquals(List.of(), acls.stream().filter(binding -> binding.pattern().resourceType() == ResourceType.GROUP)
                .collect(Collectors.toList()));

        // NO transaction ACLs must have been created
        assertEquals(List.of(),
                acls.stream().filter(binding -> binding.pattern().resourceType() == ResourceType.TRANSACTIONAL_ID)
                        .collect(Collectors.toList()));

        // NO "ALL" and no WRITE rights must have been created
        assertEquals(List.of(), acls.stream().filter(binding -> binding.entry().operation() == AclOperation.ALL)
                .collect(Collectors.toList()));
        assertEquals(List.of(), acls.stream().filter(binding -> binding.entry().operation() == AclOperation.WRITE)
                .collect(Collectors.toList()));

        // for internal, owned, and subscribed topics, DESCRIBE_CONFIGS and READ must exist
        for (AclOperationAndType op : READ_TOPIC_OPERATIONS) {
            assertTrue(acls.stream().anyMatch(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                    && binding.pattern().patternType() == PatternType.PREFIXED
                    && binding.pattern().name().equals("de.myapp.") && binding.entry().operation() == op.operation
                    && binding.entry().permissionType() == op.permissionType));

            assertTrue(acls.stream()
                    .anyMatch(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                            && binding.pattern().patternType() == PatternType.LITERAL
                            && binding.pattern().name().equals("topic1") && binding.entry().operation() == op.operation
                            && binding.entry().permissionType() == op.permissionType));

            assertTrue(acls.stream()
                    .anyMatch(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                            && binding.pattern().patternType() == PatternType.LITERAL
                            && binding.pattern().name().equals("topic2") && binding.entry().operation() == op.operation
                            && binding.entry().permissionType() == op.permissionType));
        }
    }

    @Test
    void testSimplify() {
        AclSupport support = new AclSupport(kafkaConfig, topicService, subscriptionService);

        AclBinding superfluousBinding = new AclBinding(
                new ResourcePattern(ResourceType.TOPIC, "test", PatternType.PREFIXED),
                new AccessControlEntry("me", "*", AclOperation.READ, AclPermissionType.ALLOW));

        List<AclBinding> bindings = List.of(superfluousBinding,
                new AclBinding(new ResourcePattern(ResourceType.TOPIC, "test", PatternType.PREFIXED),
                        new AccessControlEntry("me", "*", AclOperation.ALL, AclPermissionType.ALLOW)),
                new AclBinding(new ResourcePattern(ResourceType.TOPIC, "2test", PatternType.LITERAL),
                        new AccessControlEntry("me", "*", AclOperation.ALL, AclPermissionType.ALLOW)),
                new AclBinding(new ResourcePattern(ResourceType.TOPIC, "2test", PatternType.PREFIXED),
                        new AccessControlEntry("me", "*", AclOperation.READ, AclPermissionType.ALLOW)),
                new AclBinding(new ResourcePattern(ResourceType.TOPIC, "2test", PatternType.PREFIXED),
                        new AccessControlEntry("me", "*", AclOperation.CREATE, AclPermissionType.ALLOW)));

        Collection<AclBinding> reducedBindings = support.simplify(bindings);
        assertEquals(4, reducedBindings.size());
        assertFalse(reducedBindings.contains(superfluousBinding));
    }

    private DefaultAclConfig defaultAclConfig(String name, ResourceType resourceType, PatternType patternType,
            AclOperation operation) {
        DefaultAclConfig config = new DefaultAclConfig();
        config.setName(name);
        config.setResourceType(resourceType);
        config.setPatternType(patternType);
        config.setOperation(operation);
        return config;
    }

    private static class AclOperationAndType {

        private final AclOperation operation;

        private final AclPermissionType permissionType;

        private AclOperationAndType(AclOperation operation, AclPermissionType permissionType) {
            this.operation = operation;
            this.permissionType = permissionType;
        }
    }
}
