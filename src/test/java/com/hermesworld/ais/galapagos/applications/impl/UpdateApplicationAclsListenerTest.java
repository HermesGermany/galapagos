package com.hermesworld.ais.galapagos.applications.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.events.*;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.util.AclSupport;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.util.FutureUtil;
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
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateApplicationAclsListenerTest {

    @Mock
    private KafkaClusters kafkaClusters;

    @Mock
    private KafkaAuthenticationModule authenticationModule;

    @Mock
    private ApplicationsService applicationsService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private KafkaCluster cluster;

    @Mock
    private AclSupport aclSupport;

    private AclBinding dummyBinding;

    @BeforeEach
    void feedMocks() {
        when(cluster.getId()).thenReturn("_test");
        lenient().when(kafkaClusters.getEnvironment("_test")).thenReturn(Optional.of(cluster));
        lenient().when(kafkaClusters.getAuthenticationModule("_test")).thenReturn(Optional.of(authenticationModule));

        dummyBinding = new AclBinding(new ResourcePattern(ResourceType.TOPIC, "testtopic", PatternType.LITERAL),
                new AccessControlEntry("me", "*", AclOperation.ALL, AclPermissionType.ALLOW));

        BiFunction<AclBinding, String, AclBinding> withName = (binding, name) -> new AclBinding(binding.pattern(),
                new AccessControlEntry(name, binding.entry().host(), binding.entry().operation(),
                        binding.entry().permissionType()));

        lenient().when(aclSupport.getRequiredAclBindings(any(), any(), any(), anyBoolean()))
                .then(inv -> Set.of(withName.apply(dummyBinding, inv.getArgument(2))));
    }

    @Test
    void testUpdateApplicationAcls() throws InterruptedException, ExecutionException {
        when(authenticationModule
                .extractKafkaUserName(ArgumentMatchers.argThat(obj -> obj.getString("dn").equals("CN=testapp"))))
                .thenReturn("User:CN=testapp");

        when(cluster.updateUserAcls(any())).thenReturn(FutureUtil.noop());
        lenient().when(cluster.removeUserAcls(any())).thenThrow(UnsupportedOperationException.class);

        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("app01");
        metadata.setAuthenticationJson(new JSONObject(Map.of("dn", "CN=testapp")).toString());
        metadata.setConsumerGroupPrefixes(List.of("group.myapp.", "group2.myapp."));
        metadata.setInternalTopicPrefixes(List.of("de.myapp.", "de.myapp2."));
        metadata.setTransactionIdPrefixes(List.of("de.myapp."));

        GalapagosEventContext context = mock(GalapagosEventContext.class);
        when(context.getKafkaCluster()).thenReturn(cluster);

        ApplicationEvent event = new ApplicationEvent(context, metadata);

        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, subscriptionService,
                applicationsService, aclSupport);

        listener.handleApplicationRegistered(event).get();

        ArgumentCaptor<KafkaUser> captor = ArgumentCaptor.forClass(KafkaUser.class);
        verify(cluster, times(1)).updateUserAcls(captor.capture());
        assertEquals("User:CN=testapp", captor.getValue().getKafkaUserName());

        Collection<AclBinding> createdAcls = captor.getValue().getRequiredAclBindings();
        assertEquals(1, createdAcls.size());
        assertEquals("User:CN=testapp", createdAcls.iterator().next().entry().principal());
    }

    @Test
    void testHandleTopicCreated() throws ExecutionException, InterruptedException {
        GalapagosEventContext context = mock(GalapagosEventContext.class);
        when(context.getKafkaCluster()).thenReturn(cluster);
        when(cluster.getId()).thenReturn("_test");

        when(cluster.updateUserAcls(any())).thenReturn(FutureUtil.noop());

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic1");
        topic.setType(TopicType.EVENTS);
        topic.setOwnerApplicationId("producer1");

        ApplicationMetadata producer1 = new ApplicationMetadata();
        producer1.setApplicationId("producer1");
        producer1.setAuthenticationJson(new JSONObject(Map.of("dn", "CN=producer1")).toString());

        when(applicationsService.getApplicationMetadata("_test", "producer1")).thenReturn(Optional.of(producer1));
        when(authenticationModule
                .extractKafkaUserName(ArgumentMatchers.argThat(obj -> obj.getString("dn").equals("CN=producer1"))))
                .thenReturn("User:CN=producer1");

        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, subscriptionService,
                applicationsService, aclSupport);

        TopicCreatedEvent event = new TopicCreatedEvent(context, topic, new TopicCreateParams(1, 3));
        listener.handleTopicCreated(event).get();

        ArgumentCaptor<KafkaUser> captor = ArgumentCaptor.forClass(KafkaUser.class);
        verify(cluster, times(1)).updateUserAcls(captor.capture());

        assertEquals("User:CN=producer1", captor.getValue().getKafkaUserName());
    }

    @Test
    void testHandleAddProducer() throws ExecutionException, InterruptedException {
        GalapagosEventContext context = mock(GalapagosEventContext.class);
        when(context.getKafkaCluster()).thenReturn(cluster);
        when(cluster.getId()).thenReturn("_test");

        when(cluster.updateUserAcls(any())).thenReturn(FutureUtil.noop());

        ApplicationMetadata producer1 = new ApplicationMetadata();
        producer1.setApplicationId("producer1");
        producer1.setAuthenticationJson(new JSONObject(Map.of("dn", "CN=producer1")).toString());

        when(applicationsService.getApplicationMetadata("_test", "producer1")).thenReturn(Optional.of(producer1));
        when(authenticationModule
                .extractKafkaUserName(ArgumentMatchers.argThat(obj -> obj.getString("dn").equals("CN=producer1"))))
                .thenReturn("User:CN=producer1");

        TopicAddProducerEvent event = new TopicAddProducerEvent(context, "producer1", new TopicMetadata());
        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, subscriptionService,
                applicationsService, aclSupport);

        listener.handleAddTopicProducer(event).get();

        ArgumentCaptor<KafkaUser> captor = ArgumentCaptor.forClass(KafkaUser.class);
        verify(cluster, times(1)).updateUserAcls(captor.capture());

        assertEquals("User:CN=producer1", captor.getValue().getKafkaUserName());

        Collection<AclBinding> createdAcls = captor.getValue().getRequiredAclBindings();
        assertEquals(1, createdAcls.size());
        assertEquals("User:CN=producer1", createdAcls.iterator().next().entry().principal());
    }

    @Test
    void testHandleRemoveProducer() throws ExecutionException, InterruptedException {
        // more or less, this is same as add() - updateUserAcls for the removed producer must be called.
        GalapagosEventContext context = mock(GalapagosEventContext.class);
        when(context.getKafkaCluster()).thenReturn(cluster);
        when(cluster.getId()).thenReturn("_test");

        when(cluster.updateUserAcls(any())).thenReturn(FutureUtil.noop());

        ApplicationMetadata producer1 = new ApplicationMetadata();
        producer1.setApplicationId("producer1");
        producer1.setAuthenticationJson(new JSONObject(Map.of("dn", "CN=producer1")).toString());

        when(applicationsService.getApplicationMetadata("_test", "producer1")).thenReturn(Optional.of(producer1));
        when(authenticationModule
                .extractKafkaUserName(ArgumentMatchers.argThat(obj -> obj.getString("dn").equals("CN=producer1"))))
                .thenReturn("User:CN=producer1");

        TopicRemoveProducerEvent event = new TopicRemoveProducerEvent(context, "producer1", new TopicMetadata());
        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, subscriptionService,
                applicationsService, aclSupport);

        listener.handleRemoveTopicProducer(event).get();

        ArgumentCaptor<KafkaUser> captor = ArgumentCaptor.forClass(KafkaUser.class);
        verify(cluster, times(1)).updateUserAcls(captor.capture());
        assertEquals("User:CN=producer1", captor.getValue().getKafkaUserName());
    }

    @Test
    void testSubscriptionCreated() throws ExecutionException, InterruptedException {
        GalapagosEventContext context = mock(GalapagosEventContext.class);
        when(context.getKafkaCluster()).thenReturn(cluster);
        when(cluster.getId()).thenReturn("_test");

        when(cluster.updateUserAcls(any())).thenReturn(FutureUtil.noop());

        ApplicationMetadata app1 = new ApplicationMetadata();
        app1.setApplicationId("app01");
        app1.setAuthenticationJson(new JSONObject(Map.of("dn", "CN=testapp")).toString());

        when(applicationsService.getApplicationMetadata("_test", "app01")).thenReturn(Optional.of(app1));
        when(authenticationModule
                .extractKafkaUserName(ArgumentMatchers.argThat(obj -> obj.getString("dn").equals("CN=testapp"))))
                .thenReturn("User:CN=testapp");

        SubscriptionMetadata metadata = new SubscriptionMetadata();
        metadata.setClientApplicationId("app01");
        metadata.setTopicName("topic1");

        SubscriptionEvent event = new SubscriptionEvent(context, metadata);

        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, subscriptionService,
                applicationsService, aclSupport);

        listener.handleSubscriptionCreated(event).get();

        ArgumentCaptor<KafkaUser> captor = ArgumentCaptor.forClass(KafkaUser.class);
        verify(cluster, times(1)).updateUserAcls(captor.capture());

        assertEquals("User:CN=testapp", captor.getValue().getKafkaUserName());
    }

    @Test
    void testNoDeleteAclsWhenUserNameIsSame() throws Exception {
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
        when(authModule.extractKafkaUserName(any())).thenReturn("User:JohnDoe");
        when(kafkaClusters.getAuthenticationModule("_test")).thenReturn(Optional.of(authModule));
        when(cluster.updateUserAcls(any())).thenReturn(FutureUtil.noop());
        lenient().when(cluster.removeUserAcls(any())).thenReturn(FutureUtil.noop());

        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, subscriptionService,
                applicationsService, aclSupport);
        listener.handleApplicationAuthenticationChanged(event).get();

        verify(cluster).updateUserAcls(any());
        verify(cluster, times(0)).removeUserAcls(any());
    }

    @Test
    void testNoDeleteAclsWhenNoPreviousUser() throws Exception {
        GalapagosEventContext context = mock(GalapagosEventContext.class);
        when(context.getKafkaCluster()).thenReturn(cluster);

        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("app-1");
        metadata.setAuthenticationJson("{\"foo\": \"bar\" }");

        JSONObject newAuth = new JSONObject(metadata.getAuthenticationJson());

        ApplicationAuthenticationChangeEvent event = new ApplicationAuthenticationChangeEvent(context, metadata,
                new JSONObject(), newAuth);

        KafkaAuthenticationModule authModule = mock(KafkaAuthenticationModule.class);
        when(authModule.extractKafkaUserName(argThat(arg -> arg != null && arg.has("foo")))).thenReturn("User:JohnDoe");
        when(authModule.extractKafkaUserName(argThat(arg -> arg == null || !arg.has("foo")))).thenReturn(null);
        when(kafkaClusters.getAuthenticationModule("_test")).thenReturn(Optional.of(authModule));
        when(cluster.updateUserAcls(any())).thenReturn(FutureUtil.noop());
        lenient().when(cluster.removeUserAcls(any())).thenReturn(FutureUtil.noop());

        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, subscriptionService,
                applicationsService, aclSupport);
        listener.handleApplicationAuthenticationChanged(event).get();

        verify(cluster).updateUserAcls(any());
        verify(cluster, times(0)).removeUserAcls(any());
    }

    @Test
    void testNoApplicationAclUpdates() throws Exception {
        // GIVEN a configuration where noUpdateApplicationAcls flag is active
        KafkaEnvironmentConfig config = mock(KafkaEnvironmentConfig.class);
        when(config.isNoUpdateApplicationAcls()).thenReturn(true);

        when(kafkaClusters.getEnvironmentMetadata("_test")).thenReturn(Optional.of(config));
        lenient().when(cluster.updateUserAcls(any())).thenReturn(FutureUtil.noop());
        lenient().when(cluster.removeUserAcls(any())).thenReturn(FutureUtil.noop());

        GalapagosEventContext context = mock(GalapagosEventContext.class);
        when(context.getKafkaCluster()).thenReturn(cluster);

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic1");
        topic.setType(TopicType.EVENTS);
        topic.setOwnerApplicationId("producer1");

        ApplicationMetadata producer1 = new ApplicationMetadata();
        producer1.setApplicationId("producer1");
        producer1.setAuthenticationJson(new JSONObject(Map.of("dn", "CN=producer1")).toString());
        when(applicationsService.getApplicationMetadata("_test", "producer1")).thenReturn(Optional.of(producer1));

        SubscriptionMetadata subscription = new SubscriptionMetadata();
        subscription.setClientApplicationId("producer1");
        subscription.setTopicName("topic1");

        when(subscriptionService.getSubscriptionsForTopic("_test", "topic1", true)).thenReturn(List.of(subscription));

        UpdateApplicationAclsListener listener = new UpdateApplicationAclsListener(kafkaClusters, subscriptionService,
                applicationsService, aclSupport);

        // WHEN any permission-related event happens

        listener.handleApplicationRegistered(new ApplicationEvent(context, producer1)).get();
        listener.handleTopicCreated(new TopicCreatedEvent(context, topic, new TopicCreateParams(1, 3))).get();
        listener.handleTopicDeleted(new TopicEvent(context, topic)).get();
        listener.handleAddTopicProducer(new TopicAddProducerEvent(context, "producer1", topic)).get();
        listener.handleRemoveTopicProducer(new TopicRemoveProducerEvent(context, "producer1", topic)).get();
        listener.handleSubscriptionCreated(new SubscriptionEvent(context, subscription)).get();
        listener.handleSubscriptionUpdated(new SubscriptionEvent(context, subscription)).get();
        listener.handleSubscriptionDeleted(new SubscriptionEvent(context, subscription)).get();
        listener.handleApplicationAuthenticationChanged(new ApplicationAuthenticationChangeEvent(context, producer1,
                new JSONObject(Map.of("dn", "CN=producer1")), new JSONObject(Map.of("dn", "CN=producer1")))).get();
        listener.handleApplicationAuthenticationChanged(new ApplicationAuthenticationChangeEvent(context, producer1,
                new JSONObject(Map.of("dn", "CN=producer2")), new JSONObject(Map.of("dn", "CN=producer1")))).get();
        listener.handleTopicSubscriptionApprovalRequiredFlagChanged(new TopicEvent(context, topic)).get();

        // THEN NONE of these functions may change application ACLs
        verify(cluster, times(0)).updateUserAcls(any());
        verify(cluster, times(0)).removeUserAcls(any());
        // BUT every handler really checked the config
        verify(kafkaClusters, times(11)).getEnvironmentMetadata("_test");
    }

}
