package com.hermesworld.ais.galapagos.applications.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.events.*;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.DefaultAclConfig;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentsConfig;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourceType;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class UpdateApplicationAclsListenerTest {

    private static final List<AclOperation> WRITE_TOPIC_OPERATIONS = Arrays.asList(AclOperation.DESCRIBE,
            AclOperation.DESCRIBE_CONFIGS, AclOperation.READ, AclOperation.WRITE);

    private static final List<AclOperation> READ_TOPIC_OPERATIONS = Arrays.asList(AclOperation.DESCRIBE,
            AclOperation.DESCRIBE_CONFIGS, AclOperation.READ);

    private KafkaClusters kafkaClusters;
    private TopicService topicService;
    private ApplicationsService applicationsService;
    private SubscriptionService subscriptionService;
    private KafkaCluster cluster;
    private KafkaEnvironmentsConfig kafkaConfig;

    @Before
    public void feedMocks() {
        topicService = mock(TopicService.class);
        applicationsService = mock(ApplicationsService.class);
        subscriptionService = mock(SubscriptionService.class);
        kafkaClusters = mock(KafkaClusters.class);
        kafkaConfig = mock(KafkaEnvironmentsConfig.class);

        cluster = mock(KafkaCluster.class);
        when(cluster.getId()).thenReturn("_test");
        when(kafkaClusters.getEnvironment("_test")).thenReturn(Optional.of(cluster));
    }

    @Test
    public void testUpdateApplicationAcls() throws InterruptedException, ExecutionException {
        KafkaAuthenticationModule module = mock(KafkaAuthenticationModule.class);
        when(module.extractKafkaUserName(ArgumentMatchers.matches("app01"),
                ArgumentMatchers.argThat(obj -> obj.getString("dn").equals("CN=testapp"))))
                        .thenReturn("User:CN=testapp");
        when(kafkaClusters.getAuthenticationModule("_test")).thenReturn(Optional.of(module));

        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, topicService,
                subscriptionService, applicationsService, kafkaConfig);

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

        assertEquals(8, createdAcls.size());

        // check that cluster DESCRIBE and DESCRIBE_CONFIGS right is included
        assertNotNull("No DESCRIBE_CONFIGS right for cluster included",
                createdAcls.stream()
                        .filter(b -> b.pattern().resourceType() == ResourceType.CLUSTER
                                && b.pattern().patternType() == PatternType.LITERAL
                                && b.pattern().name().equals("kafka-cluster")
                                && b.entry().permissionType() == AclPermissionType.ALLOW
                                && b.entry().operation() == AclOperation.DESCRIBE_CONFIGS)
                        .findAny().orElse(null));
        assertNotNull("No DESCRIBE right for cluster included",
                createdAcls.stream()
                        .filter(b -> b.pattern().resourceType() == ResourceType.CLUSTER
                                && b.pattern().patternType() == PatternType.LITERAL
                                && b.pattern().name().equals("kafka-cluster")
                                && b.entry().permissionType() == AclPermissionType.ALLOW
                                && b.entry().operation() == AclOperation.DESCRIBE)
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
                subscriptionService, applicationsService, kafkaConfig);

        Collection<AclBinding> bindings = listener.getApplicationUser(app1, "_test").getRequiredAclBindings();

        assertEquals(3, bindings.size());
        assertFalse(bindings.stream().anyMatch(binding -> binding.pattern().resourceType() == ResourceType.TOPIC));
    }

    @Test
    public void addedProducerGetsCorrectAcls() throws ExecutionException, InterruptedException {
        KafkaAuthenticationModule module = mock(KafkaAuthenticationModule.class);
        when(module.extractKafkaUserName(any(), any())).thenReturn("producer1");
        when(kafkaClusters.getAuthenticationModule("_test")).thenReturn(Optional.of(module));
        GalapagosEventContext context = mock(GalapagosEventContext.class);
        when(context.getKafkaCluster()).thenReturn(cluster);
        when(cluster.getId()).thenReturn("_test");

        List<KafkaUser> users = new ArrayList<>();

        when(cluster.updateUserAcls(any())).then(inv -> {
            users.add(inv.getArgument(0));
            return CompletableFuture.completedFuture(null);
        });

        ApplicationMetadata producer1 = new ApplicationMetadata();
        producer1.setApplicationId("producer1");
        producer1.setAuthenticationJson(new JSONObject("{}").toString());

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic1");
        topic.setType(TopicType.EVENTS);
        topic.setProducers(List.of("producer1"));
        when(topicService.listTopics("_test")).thenReturn(List.of(topic));

        when(applicationsService.getApplicationMetadata("_test", "producer1")).thenReturn(Optional.of(producer1));

        TopicAddProducersEvent event = new TopicAddProducersEvent(context, Collections.singletonList("producer1"),
                topic);
        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, topicService,
                subscriptionService, applicationsService, kafkaConfig);

        listener.handleAddTopicProducers(event).get();

        Collection<AclBinding> createdAcls = users.get(0).getRequiredAclBindings();

        verify(cluster, times(1)).updateUserAcls(any());

        WRITE_TOPIC_OPERATIONS.forEach(op -> assertNotNull("Did not find expected write ACL for topic",
                createdAcls.stream()
                        .filter(binding -> binding.pattern().resourceType() == ResourceType.TOPIC
                                && binding.pattern().patternType() == PatternType.LITERAL
                                && binding.pattern().name().equals("topic1")
                                && binding.entry().operation().equals(AclOperation.WRITE))
                        .findAny().orElse(null)));
    }

    @Test
    public void deletedProducerHasNoAcls() throws ExecutionException, InterruptedException {
        KafkaAuthenticationModule module = mock(KafkaAuthenticationModule.class);
        when(module.extractKafkaUserName(any(), any())).thenReturn("producer1");
        when(kafkaClusters.getAuthenticationModule("_test")).thenReturn(Optional.of(module));
        GalapagosEventContext context = mock(GalapagosEventContext.class);
        when(context.getKafkaCluster()).thenReturn(cluster);
        when(cluster.getId()).thenReturn("_test");

        List<KafkaUser> users = new ArrayList<>();

        when(cluster.updateUserAcls(any())).then(inv -> {
            users.add(inv.getArgument(0));
            return CompletableFuture.completedFuture(null);
        });

        ApplicationMetadata app = new ApplicationMetadata();
        app.setApplicationId("app1");
        app.setAuthenticationJson(new JSONObject("{}").toString());

        ApplicationMetadata producer1 = new ApplicationMetadata();
        producer1.setApplicationId("producer1");
        producer1.setAuthenticationJson(new JSONObject("{}").toString());

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic1");
        topic.setType(TopicType.EVENTS);
        topic.setOwnerApplicationId("app1");
        when(topicService.listTopics("_test")).thenReturn(List.of(topic));

        when(applicationsService.getApplicationMetadata("_test", "producer1")).thenReturn(Optional.of(producer1));

        TopicRemoveProducerEvent event = new TopicRemoveProducerEvent(context, "producer1", topic);
        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, topicService,
                subscriptionService, applicationsService, kafkaConfig);

        listener.handleRemoveTopicProducer(event).get();

        Collection<AclBinding> acls = users.get(0).getRequiredAclBindings();

        verify(cluster, times(1)).updateUserAcls(any());
        assertFalse(acls.stream().anyMatch(binding -> binding.pattern().resourceType() == ResourceType.TOPIC));

    }

    @Test
    public void testNoDeleteAclsWhenUserNameIsSame() throws Exception {
        // tests that, when an AuthenticationChanged event occurs but the resulting Kafka User Name is the same, the
        // listener does not delete the ACLs of the user after updating them (because that would result in zero ACLs).
        GalapagosEventContext context = mock(GalapagosEventContext.class);
        when(context.getKafkaCluster()).thenReturn(cluster);

        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("app-1");
        metadata.setAuthenticationJson("{ }");

        JSONObject oldAuth = new JSONObject();
        JSONObject newAuth = new JSONObject();

        ApplicationAuthenticationChangeEvent event = new ApplicationAuthenticationChangeEvent(context, metadata,
                oldAuth, newAuth);

        KafkaAuthenticationModule authModule = mock(KafkaAuthenticationModule.class);
        when(authModule.extractKafkaUserName(any(), any())).thenReturn("User:JohnDoe");
        when(kafkaClusters.getAuthenticationModule("_test")).thenReturn(Optional.of(authModule));
        when(cluster.updateUserAcls(any())).thenReturn(FutureUtil.noop());
        when(cluster.removeUserAcls(any())).thenReturn(FutureUtil.noop());

        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, topicService,
                subscriptionService, applicationsService, kafkaConfig);
        listener.handleApplicationAuthenticationChanged(event).get();

        verify(cluster).updateUserAcls(any());
        verify(cluster, times(0)).removeUserAcls(any());
    }

    @Test
    public void testNoDeleteAclsWhenUNoPreviousUser() throws Exception {
        // tests that, when an AuthenticationChanged event occurs but the resulting Kafka User Name is the same, the
        // listener does not delete the ACLs of the user after updating them (because that would result in zero ACLs).
        GalapagosEventContext context = mock(GalapagosEventContext.class);
        when(context.getKafkaCluster()).thenReturn(cluster);

        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("app-1");
        metadata.setAuthenticationJson("{\"foo\": \"bar\" }");

        JSONObject newAuth = new JSONObject(metadata.getAuthenticationJson());

        ApplicationAuthenticationChangeEvent event = new ApplicationAuthenticationChangeEvent(context, metadata,
                new JSONObject(), newAuth);

        KafkaAuthenticationModule authModule = mock(KafkaAuthenticationModule.class);
        when(authModule.extractKafkaUserName(any(), argThat(arg -> arg != null && arg.has("foo"))))
                .thenReturn("User:JohnDoe");
        when(authModule.extractKafkaUserName(any(), argThat(arg -> arg == null || !arg.has("foo")))).thenReturn(null);
        when(kafkaClusters.getAuthenticationModule("_test")).thenReturn(Optional.of(authModule));
        when(cluster.updateUserAcls(any())).thenReturn(FutureUtil.noop());
        when(cluster.removeUserAcls(any())).thenReturn(FutureUtil.noop());

        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, topicService,
                subscriptionService, applicationsService, kafkaConfig);
        listener.handleApplicationAuthenticationChanged(event).get();

        verify(cluster).updateUserAcls(any());
        verify(cluster, times(0)).removeUserAcls(any());
    }

    @Test
    public void testDefaultAcls() {
        KafkaAuthenticationModule module = mock(KafkaAuthenticationModule.class);
        when(module.extractKafkaUserName(ArgumentMatchers.matches("app-1"),
                ArgumentMatchers.argThat(obj -> obj.getString("dn").equals("CN=testapp"))))
                        .thenReturn("User:CN=testapp");
        when(kafkaClusters.getAuthenticationModule("_test")).thenReturn(Optional.of(module));

        ApplicationMetadata app1 = new ApplicationMetadata();
        app1.setApplicationId("app-1");
        app1.setAuthenticationJson(new JSONObject(Map.of("dn", "CN=testapp")).toString());
        app1.setConsumerGroupPrefixes(List.of("groups."));

        List<DefaultAclConfig> defaultAcls = new ArrayList<>();
        defaultAcls.add(defaultAclConfig("test-group", ResourceType.GROUP, PatternType.PREFIXED, AclOperation.READ));
        defaultAcls.add(defaultAclConfig("test-topic", ResourceType.TOPIC, PatternType.LITERAL, AclOperation.CREATE));
        when(kafkaConfig.getDefaultAcls()).thenReturn(defaultAcls);

        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, topicService,
                subscriptionService, applicationsService, kafkaConfig);

        Collection<AclBinding> bindings = listener.getApplicationUser(app1, "_test").getRequiredAclBindings();

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
