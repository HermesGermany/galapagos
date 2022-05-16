package com.hermesworld.ais.galapagos.kafka.util;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.kafka.config.DefaultAclConfig;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentsConfig;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourceType;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AclSupportTest {

    private static final List<AclOperation> WRITE_TOPIC_OPERATIONS = Arrays.asList(AclOperation.DESCRIBE,
            AclOperation.DESCRIBE_CONFIGS, AclOperation.READ, AclOperation.WRITE);

    private static final List<AclOperation> READ_TOPIC_OPERATIONS = Arrays.asList(AclOperation.DESCRIBE,
            AclOperation.DESCRIBE_CONFIGS, AclOperation.READ);

    @Mock
    private KafkaEnvironmentsConfig kafkaConfig;

    @Mock
    private TopicService topicService;

    @Mock
    private SubscriptionService subscriptionService;

    @BeforeEach
    public void initMocks() {

    }

    @Test
    public void testGetRequiredAclBindings_simple() {
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
        when(subscriptionService.getSubscriptionsOfApplication("_test", "app01", false))
                .thenReturn(Collections.singletonList(sub));

        AclSupport aclSupport = new AclSupport(kafkaConfig, topicService, subscriptionService);

        Collection<AclBinding> acls = aclSupport.getRequiredAclBindings("_test", metadata, "User:CN=testapp", false);

        assertEquals(15, acls.size());

        // check that cluster DESCRIBE and DESCRIBE_CONFIGS right is included
        assertNotNull(
                acls.stream()
                        .filter(b -> b.pattern().resourceType() == ResourceType.CLUSTER
                                && b.pattern().patternType() == PatternType.LITERAL
                                && b.pattern().name().equals("kafka-cluster")
                                && b.entry().permissionType() == AclPermissionType.ALLOW
                                && b.entry().operation() == AclOperation.DESCRIBE_CONFIGS)
                        .findAny().orElse(null),
                "No DESCRIBE_CONFIGS right for cluster included");
        assertNotNull(
                acls.stream()
                        .filter(b -> b.pattern().resourceType() == ResourceType.CLUSTER
                                && b.pattern().patternType() == PatternType.LITERAL
                                && b.pattern().name().equals("kafka-cluster")
                                && b.entry().permissionType() == AclPermissionType.ALLOW
                                && b.entry().operation() == AclOperation.DESCRIBE)
                        .findAny().orElse(null),
                "No DESCRIBE right for cluster included");

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
                        && binding.entry().operation() == AclOperation.DESCRIBE)
                .findAny().orElse(null));
        assertNotNull(acls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.TRANSACTIONAL_ID
                        && binding.pattern().patternType() == PatternType.PREFIXED
                        && binding.pattern().name().equals("de.myapp.")
                        && binding.entry().operation() == AclOperation.WRITE)
                .findAny().orElse(null));

        // Write rights for owned topic must also be present
        WRITE_TOPIC_OPERATIONS.forEach(op -> assertNotNull(acls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                        && binding.pattern().patternType() == PatternType.LITERAL
                        && binding.pattern().name().equals("topic1") && binding.entry().operation() == op)));

        // and read rights for subscribed topic
        READ_TOPIC_OPERATIONS.forEach(op -> assertNotNull(acls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                        && binding.pattern().patternType() == PatternType.LITERAL
                        && binding.pattern().name().equals("topic2") && binding.entry().operation() == op)));
    }

    @Test
    public void testNoWriteAclsForInternalTopics() {
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

        assertEquals(3, bindings.size());
        assertFalse(bindings.stream().anyMatch(binding -> binding.pattern().resourceType() == ResourceType.TOPIC));
    }

    @Test
    public void testAdditionalProducerWriteAccess() {
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
                bindings.stream()
                        .filter(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                                && binding.pattern().patternType() == PatternType.LITERAL
                                && binding.pattern().name().equals("topic1") && binding.entry().operation() == op
                                && binding.entry().principal().equals("User:CN=producer1"))
                        .findAny().orElse(null),
                "Did not find expected write ACL for topic (operation " + op.name() + " is missing)"));
    }

    @Test
    public void testDefaultAcls() {
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

    private DefaultAclConfig defaultAclConfig(String name, ResourceType resourceType, PatternType patternType,
            AclOperation operation) {
        DefaultAclConfig config = new DefaultAclConfig();
        config.setName(name);
        config.setResourceType(resourceType);
        config.setPatternType(patternType);
        config.setOperation(operation);
        return config;
    }
}
