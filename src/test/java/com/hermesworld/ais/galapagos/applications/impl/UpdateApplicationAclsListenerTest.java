package com.hermesworld.ais.galapagos.applications.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.events.ApplicationEvent;
import com.hermesworld.ais.galapagos.events.GalapagosEventContext;
import com.hermesworld.ais.galapagos.events.SubscriptionEvent;
import com.hermesworld.ais.galapagos.events.TopicCreatedEvent;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
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
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UpdateApplicationAclsListenerTest {

    private static final List<AclOperation> WRITE_TOPIC_OPERATIONS = Arrays.asList(AclOperation.DESCRIBE,
            AclOperation.DESCRIBE_CONFIGS, AclOperation.READ, AclOperation.WRITE);

    private static final List<AclOperation> READ_TOPIC_OPERATIONS = Arrays.asList(AclOperation.DESCRIBE,
            AclOperation.DESCRIBE_CONFIGS, AclOperation.READ);

    @Test
    public void testUpdateApplicationAcls() throws InterruptedException, ExecutionException {
        TopicService topicService = mock(TopicService.class);
        ApplicationsService applicationsService = mock(ApplicationsService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        KafkaClusters kafkaClusters = mock(KafkaClusters.class);

        KafkaCluster cluster = mock(KafkaCluster.class);
        when(cluster.getId()).thenReturn("_test");
        when(kafkaClusters.getEnvironment("_test")).thenReturn(Optional.of(cluster));

        KafkaAuthenticationModule module = mock(KafkaAuthenticationModule.class);
        when(module.extractKafkaUserName(ArgumentMatchers.matches("app01"),
                ArgumentMatchers.argThat(obj -> obj.getString("dn").equals("CN=testapp"))))
                        .thenReturn("User:CN=testapp");
        when(kafkaClusters.getAuthenticationModule("_test")).thenReturn(Optional.of(module));

        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, topicService,
                subscriptionService, applicationsService);

        List<KafkaUser> users = new ArrayList<>();

        when(cluster.updateUserAcls(any())).then(inv -> {
            users.add(inv.getArgument(0));
            return CompletableFuture.completedFuture(null);
        });

        when(cluster.removeUserAcls(any())).thenThrow(UnsupportedOperationException.class);

        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("app01");
        metadata.setAuthenticationJson(new JSONObject(Map.of("dn", "CN=testapp")).toString());
        metadata.setConsumerGroupPrefixes(List.of("group.myapp.", "group2.myapp."));
        metadata.setInternalTopicPrefixes(List.of("de.myapp.", "de.myapp2."));
        metadata.setTransactionIdPrefixes(List.of("de.myapp."));

        GalapagosEventContext context = mock(GalapagosEventContext.class);
        when(context.getKafkaCluster()).thenReturn(cluster);
        when(applicationsService.getApplicationMetadata("_test", "app01")).thenReturn(Optional.of(metadata));

        ApplicationEvent event = new ApplicationEvent(context, metadata);

        listener.handleApplicationRegistered(event).get();

        assertEquals(1, users.size());
        Collection<AclBinding> createdAcls = users.get(0).getRequiredAclBindings();

        assertEquals(7, createdAcls.size());

        // check that cluster DESCRIBE_CONFIGS right is included
        assertNotNull("No DESCRIBE_CONFIGS right for cluster included",
                createdAcls.stream()
                        .filter(b -> b.pattern().resourceType() == ResourceType.CLUSTER
                                && b.pattern().patternType() == PatternType.LITERAL
                                && b.pattern().name().equals("kafka-cluster")
                                && b.entry().permissionType() == AclPermissionType.ALLOW
                                && b.entry().operation() == AclOperation.DESCRIBE_CONFIGS)
                        .findAny().orElse(null));

        // two ACL for groups and two for topic prefixes must have been created
        assertNotNull(createdAcls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                        && binding.pattern().patternType() == PatternType.PREFIXED
                        && binding.pattern().name().equals("de.myapp.")
                        && binding.entry().operation().equals(AclOperation.ALL))
                .findAny().orElse(null));
        assertNotNull(createdAcls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                        && binding.pattern().patternType() == PatternType.PREFIXED
                        && binding.pattern().name().equals("de.myapp2.")
                        && binding.entry().operation().equals(AclOperation.ALL))
                .findAny().orElse(null));
        assertNotNull(createdAcls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.GROUP
                        && binding.pattern().patternType() == PatternType.PREFIXED
                        && binding.pattern().name().equals("group.myapp."))
                .findAny().orElse(null));
        assertNotNull(createdAcls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.GROUP
                        && binding.pattern().patternType() == PatternType.PREFIXED
                        && binding.pattern().name().equals("group2.myapp."))
                .findAny().orElse(null));

        // transaction ACLs must have been created
        assertNotNull(createdAcls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.TRANSACTIONAL_ID
                        && binding.pattern().patternType() == PatternType.PREFIXED
                        && binding.pattern().name().equals("de.myapp.")
                        && binding.entry().operation() == AclOperation.DESCRIBE)
                .findAny().orElse(null));
        assertNotNull(createdAcls.stream()
                .filter(binding -> binding.pattern().resourceType() == ResourceType.TRANSACTIONAL_ID
                        && binding.pattern().patternType() == PatternType.PREFIXED
                        && binding.pattern().name().equals("de.myapp.")
                        && binding.entry().operation() == AclOperation.WRITE)
                .findAny().orElse(null));

        Collection<AclBinding> alreadyTested = new ArrayList<>(createdAcls);

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic1");
        topic.setType(TopicType.EVENTS);
        topic.setOwnerApplicationId("app01");

        when(topicService.listTopics("_test")).thenReturn(Collections.singletonList(topic));

        TopicCreatedEvent tcevent = new TopicCreatedEvent(context, topic, new TopicCreateParams(2, 3));
        users.clear();

        listener.handleTopicCreated(tcevent).get();

        assertEquals(1, users.size());

        createdAcls.clear();
        createdAcls.addAll(users.get(0).getRequiredAclBindings());
        createdAcls.removeAll(alreadyTested);

        // WRITE rights must have been given
        assertEquals(4, createdAcls.size());
        WRITE_TOPIC_OPERATIONS.forEach(op -> assertNotNull("Did not find expected write ACL for topic",
                createdAcls.stream()
                        .filter(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                                && binding.pattern().patternType() == PatternType.LITERAL
                                && binding.pattern().name().equals("topic1") && binding.entry().operation() == op)
                        .findAny().orElse(null)));

        alreadyTested.addAll(createdAcls);

        SubscriptionMetadata sub = new SubscriptionMetadata();
        sub.setId("1");
        sub.setClientApplicationId("app01");
        sub.setTopicName("topic2");

        when(subscriptionService.getSubscriptionsOfApplication("_test", "app01", false))
                .thenReturn(Collections.singletonList(sub));

        TopicMetadata topic2 = new TopicMetadata();
        topic2.setName("topic2");
        topic2.setType(TopicType.EVENTS);
        topic2.setOwnerApplicationId("app02");

        when(topicService.getTopic("_test", "topic2")).thenReturn(Optional.of(topic2));

        createdAcls.clear();
        users.clear();

        listener.handleSubscriptionCreated(new SubscriptionEvent(context, sub)).get();

        assertEquals(1, users.size());
        createdAcls.addAll(users.get(0).getRequiredAclBindings());
        createdAcls.removeAll(alreadyTested);

        // READ rights must have been given
        assertEquals(3, createdAcls.size());
        READ_TOPIC_OPERATIONS
                .forEach(op -> assertNotNull("Did not find expected read ACL for topic",
                        createdAcls.stream().filter(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                                && binding.pattern().patternType() == PatternType.LITERAL
                                && binding.pattern().name().equals("topic2") && binding.entry().operation() == op
                                && binding.entry().principal().equals("User:CN=testapp")).findAny().orElse(null)));
    }

    @Test
    public void testNoWriteAclsForInternalTopics() {
        TopicService topicService = mock(TopicService.class);
        ApplicationsService applicationsService = mock(ApplicationsService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        KafkaClusters kafkaClusters = mock(KafkaClusters.class);

        KafkaCluster cluster = mock(KafkaCluster.class);
        when(cluster.getId()).thenReturn("_test");
        when(kafkaClusters.getEnvironment("_test")).thenReturn(Optional.of(cluster));

        KafkaAuthenticationModule module = mock(KafkaAuthenticationModule.class);
        when(module.extractKafkaUserName(ArgumentMatchers.matches("app-1"),
                ArgumentMatchers.argThat(obj -> obj.getString("dn").equals("CN=testapp"))))
                        .thenReturn("User:CN=testapp");
        when(kafkaClusters.getAuthenticationModule("_test")).thenReturn(Optional.of(module));

        ApplicationMetadata app1 = new ApplicationMetadata();
        app1.setApplicationId("app-1");
        app1.setAuthenticationJson(new JSONObject(Map.of("dn", "CN=testapp")).toString());
        app1.setConsumerGroupPrefixes(List.of("groups."));

        TopicMetadata topic = new TopicMetadata();
        topic.setName("int1");
        topic.setType(TopicType.INTERNAL);
        topic.setOwnerApplicationId("app-1");

        when(topicService.listTopics("_test")).thenReturn(List.of(topic));

        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, topicService,
                subscriptionService, applicationsService);

        Collection<AclBinding> bindings = listener.getApplicationUser(app1, "_test").getRequiredAclBindings();

        assertEquals(2, bindings.size());
        assertFalse(bindings.stream().anyMatch(binding -> binding.pattern().resourceType() == ResourceType.TOPIC));
    }
}
