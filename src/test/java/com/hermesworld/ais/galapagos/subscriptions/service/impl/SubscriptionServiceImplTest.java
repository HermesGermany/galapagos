package com.hermesworld.ais.galapagos.subscriptions.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.events.GalapagosEventManagerMock;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionState;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionServiceImplTest {

    private KafkaClusters kafkaClusters;

    private ApplicationsService applicationsService;

    private TopicService topicService;

    private GalapagosEventManagerMock eventManager;

    private TopicBasedRepositoryMock<SubscriptionMetadata> repository;

    @BeforeEach
    void setupMocks() {
        kafkaClusters = mock(KafkaClusters.class);

        KafkaCluster testCluster = mock(KafkaCluster.class);
        repository = new TopicBasedRepositoryMock<>();
        when(testCluster.getRepository("subscriptions", SubscriptionMetadata.class)).thenReturn(repository);
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));

        ApplicationMetadata app1 = new ApplicationMetadata();
        app1.setApplicationId("app-1");
        ApplicationMetadata app2 = new ApplicationMetadata();
        app2.setApplicationId("app-2");

        applicationsService = mock(ApplicationsService.class);
        when(applicationsService.getApplicationMetadata("test", "app-1")).thenReturn(Optional.of(app1));
        when(applicationsService.getApplicationMetadata("test", "app-2")).thenReturn(Optional.of(app2));

        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setType(TopicType.EVENTS);
        topic1.setOwnerApplicationId("app-2");
        topic1.setSubscriptionApprovalRequired(false);
        TopicMetadata topic2 = new TopicMetadata();
        topic2.setName("topic-2");
        topic2.setType(TopicType.INTERNAL);
        topic2.setOwnerApplicationId("app-2");
        TopicMetadata topic3 = new TopicMetadata();
        topic3.setName("topic-3");
        topic3.setType(TopicType.EVENTS);
        topic3.setOwnerApplicationId("app-2");
        topic3.setSubscriptionApprovalRequired(true);

        topicService = mock(TopicService.class);
        when(topicService.getTopic("test", "topic-1")).thenReturn(Optional.of(topic1));
        when(topicService.getTopic("test", "topic-2")).thenReturn(Optional.of(topic2));
        when(topicService.getTopic("test", "topic-3")).thenReturn(Optional.of(topic3));

        eventManager = new GalapagosEventManagerMock();
    }

    @Test
    void testAddSubscription_positive() throws Exception {
        SubscriptionServiceImpl service = new SubscriptionServiceImpl(kafkaClusters, applicationsService, topicService,
                eventManager);

        SubscriptionMetadata metadata = service.addSubscription("test", "topic-1", "app-1", "Some Desc").get();
        assertNotNull(metadata.getId());
        assertEquals("topic-1", metadata.getTopicName());
        assertEquals("app-1", metadata.getClientApplicationId());
        assertEquals(SubscriptionState.APPROVED, metadata.getState());
        assertEquals("Some Desc", metadata.getDescription());

        assertNotNull(repository.getObject(metadata.getId()));

        assertEquals(1, eventManager.getSinkInvocations().size());
        assertEquals("handleSubscriptionCreated", eventManager.getSinkInvocations().get(0).getMethod().getName());
    }

    @Test
    void testAddSubscription_pending() throws Exception {
        SubscriptionServiceImpl service = new SubscriptionServiceImpl(kafkaClusters, applicationsService, topicService,
                eventManager);

        SubscriptionMetadata metadata = service.addSubscription("test", "topic-3", "app-1", null).get();
        assertNotNull(metadata.getId());
        assertEquals("topic-3", metadata.getTopicName());
        assertEquals("app-1", metadata.getClientApplicationId());
        assertEquals(SubscriptionState.PENDING, metadata.getState());
        assertNull(metadata.getDescription());

        assertNotNull(repository.getObject(metadata.getId()));

        assertEquals(1, eventManager.getSinkInvocations().size());
        assertEquals("handleSubscriptionCreated", eventManager.getSinkInvocations().get(0).getMethod().getName());
    }

    @Test
    void testAddSubscription_fail_internalTopic() throws Exception {
        SubscriptionServiceImpl service = new SubscriptionServiceImpl(kafkaClusters, applicationsService, topicService,
                eventManager);

        try {
            service.addSubscription("test", "topic-2", "app-1", null).get();
            fail("Expected exception when trying to subscribe an internal topic");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }

        assertEquals(0, eventManager.getSinkInvocations().size());
    }

    @Test
    void testAddSubscription_directMetadata() throws Exception {
        SubscriptionMetadata sub = new SubscriptionMetadata();
        sub.setId("123");
        sub.setState(SubscriptionState.APPROVED);
        sub.setClientApplicationId("app-1");
        sub.setTopicName("topic-3");

        SubscriptionServiceImpl service = new SubscriptionServiceImpl(kafkaClusters, applicationsService, topicService,
                eventManager);

        SubscriptionMetadata createdMeta = service.addSubscription("test", sub).get();
        assertNotEquals(sub.getId(), createdMeta.getId());
        assertEquals(sub.getState(), createdMeta.getState());
        assertEquals(sub.getClientApplicationId(), createdMeta.getClientApplicationId());
        assertEquals(sub.getTopicName(), createdMeta.getTopicName());
    }

    @Test
    void testUpdateSubscriptionState_positive() throws Exception {
        SubscriptionMetadata sub = new SubscriptionMetadata();
        sub.setId("123");
        sub.setState(SubscriptionState.PENDING);
        sub.setClientApplicationId("app-1");
        sub.setTopicName("topic-3");

        repository.save(sub).get();

        SubscriptionServiceImpl service = new SubscriptionServiceImpl(kafkaClusters, applicationsService, topicService,
                eventManager);

        service.updateSubscriptionState("test", "123", SubscriptionState.APPROVED).get();

        assertEquals(1, eventManager.getSinkInvocations().size());
        assertEquals("handleSubscriptionUpdated", eventManager.getSinkInvocations().get(0).getMethod().getName());

        SubscriptionMetadata savedSub = repository.getObject("123").orElseThrow();

        assertEquals(SubscriptionState.APPROVED, savedSub.getState());
    }

    @Test
    void testUpdateSubscriptionState_rejected_deletes() throws Exception {
        SubscriptionMetadata sub = new SubscriptionMetadata();
        sub.setId("123");
        sub.setState(SubscriptionState.PENDING);
        sub.setClientApplicationId("app-1");
        sub.setTopicName("topic-3");

        repository.save(sub).get();

        SubscriptionServiceImpl service = new SubscriptionServiceImpl(kafkaClusters, applicationsService, topicService,
                eventManager);

        service.updateSubscriptionState("test", "123", SubscriptionState.REJECTED).get();

        assertEquals(1, eventManager.getSinkInvocations().size());
        assertEquals("handleSubscriptionUpdated", eventManager.getSinkInvocations().get(0).getMethod().getName());

        // subscription should instantly be deleted
        assertFalse(repository.getObject("123").isPresent());
    }

    @Test
    void testUpdateSubscriptionState_canceled_deletes() throws Exception {
        SubscriptionMetadata sub = new SubscriptionMetadata();
        sub.setId("123");
        sub.setState(SubscriptionState.APPROVED);
        sub.setClientApplicationId("app-1");
        sub.setTopicName("topic-3");

        repository.save(sub).get();

        SubscriptionServiceImpl service = new SubscriptionServiceImpl(kafkaClusters, applicationsService, topicService,
                eventManager);

        service.updateSubscriptionState("test", "123", SubscriptionState.CANCELED).get();

        assertEquals(1, eventManager.getSinkInvocations().size());
        assertEquals("handleSubscriptionUpdated", eventManager.getSinkInvocations().get(0).getMethod().getName());

        // subscription should instantly be deleted
        assertFalse(repository.getObject("123").isPresent());
    }

    @Test
    void testUpdateSubscriptionState_rejected_mapsToCanceled() throws Exception {
        SubscriptionMetadata sub = new SubscriptionMetadata();
        sub.setId("123");
        sub.setState(SubscriptionState.APPROVED);
        sub.setClientApplicationId("app-1");
        sub.setTopicName("topic-3");

        repository.save(sub).get();

        SubscriptionServiceImpl service = new SubscriptionServiceImpl(kafkaClusters, applicationsService, topicService,
                eventManager);

        service.updateSubscriptionState("test", "123", SubscriptionState.REJECTED).get();

        assertEquals(1, eventManager.getSinkInvocations().size());
        assertEquals("handleSubscriptionUpdated", eventManager.getSinkInvocations().get(0).getMethod().getName());
        SubscriptionMetadata metadata = eventManager.getSinkInvocations().get(0).getArgument(0);
        assertEquals(SubscriptionState.CANCELED, metadata.getState());

        // subscription should instantly be deleted
        assertFalse(repository.getObject("123").isPresent());
    }

    @Test
    void testUpdateSubscriptionState_noop() throws Exception {
        SubscriptionMetadata sub = new SubscriptionMetadata();
        sub.setId("123");
        sub.setState(SubscriptionState.APPROVED);
        sub.setClientApplicationId("app-1");
        sub.setTopicName("topic-3");

        repository.save(sub).get();

        SubscriptionServiceImpl service = new SubscriptionServiceImpl(kafkaClusters, applicationsService, topicService,
                eventManager);

        service.updateSubscriptionState("test", "123", SubscriptionState.APPROVED).get();

        assertEquals(0, eventManager.getSinkInvocations().size());
        assertSame(repository.getObject("123").orElseThrow(), sub); // intentionally object identity
    }

    @Test
    void testDeleteSubscription() throws Exception {
        SubscriptionMetadata sub = new SubscriptionMetadata();
        sub.setId("123");
        sub.setState(SubscriptionState.APPROVED);
        sub.setClientApplicationId("app-1");
        sub.setTopicName("topic-3");

        repository.save(sub).get();

        SubscriptionServiceImpl service = new SubscriptionServiceImpl(kafkaClusters, applicationsService, topicService,
                eventManager);

        service.deleteSubscription("test", "123").get();

        assertEquals(1, eventManager.getSinkInvocations().size());
        assertEquals("handleSubscriptionDeleted", eventManager.getSinkInvocations().get(0).getMethod().getName());
        assertFalse(repository.getObject("123").isPresent());
    }

    @Test
    void testGetSubscriptionsForTopic() throws Exception {
        SubscriptionMetadata sub1 = new SubscriptionMetadata();
        sub1.setId("123");
        sub1.setState(SubscriptionState.APPROVED);
        sub1.setClientApplicationId("app-1");
        sub1.setTopicName("topic-3");
        SubscriptionMetadata sub2 = new SubscriptionMetadata();
        sub2.setId("124");
        sub2.setState(SubscriptionState.PENDING);
        sub2.setClientApplicationId("app-3");
        sub2.setTopicName("topic-3");

        repository.save(sub1).get();
        repository.save(sub2).get();

        SubscriptionServiceImpl service = new SubscriptionServiceImpl(kafkaClusters, applicationsService, topicService,
                eventManager);

        List<SubscriptionMetadata> result = service.getSubscriptionsForTopic("test", "topic-3", false);
        assertEquals(1, result.size());
        assertEquals("123", result.get(0).getId());

        result = service.getSubscriptionsForTopic("test", "topic-3", true);
        assertEquals(2, result.size());
        assertEquals("123", result.get(0).getId());
    }

    @Test
    void testGetSubscriptionsOfApplication() throws Exception {
        SubscriptionMetadata sub1 = new SubscriptionMetadata();
        sub1.setId("123");
        sub1.setState(SubscriptionState.APPROVED);
        sub1.setClientApplicationId("app-1");
        sub1.setTopicName("topic-3");
        SubscriptionMetadata sub2 = new SubscriptionMetadata();
        sub2.setId("124");
        sub2.setState(SubscriptionState.PENDING);
        sub2.setClientApplicationId("app-3");
        sub2.setTopicName("topic-3");

        repository.save(sub1).get();
        repository.save(sub2).get();

        SubscriptionServiceImpl service = new SubscriptionServiceImpl(kafkaClusters, applicationsService, topicService,
                eventManager);

        List<SubscriptionMetadata> result = service.getSubscriptionsOfApplication("test", "app-1", false);
        assertEquals(1, result.size());
        assertEquals("123", result.get(0).getId());
        result = service.getSubscriptionsOfApplication("test", "app-3", false);
        assertEquals(0, result.size());

        result = service.getSubscriptionsOfApplication("test", "app-3", true);
        assertEquals(1, result.size());
        assertEquals("124", result.get(0).getId());
    }

}
