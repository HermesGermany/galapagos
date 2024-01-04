package com.hermesworld.ais.galapagos.subscriptions;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.subscriptions.controller.SubscriptionsController;
import com.hermesworld.ais.galapagos.subscriptions.controller.UpdateSubscriptionDto;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import com.hermesworld.ais.galapagos.util.FutureUtil;

class SubscriptionsControllerTest {

    private KafkaClusters kafkaClusters;

    private SubscriptionService subscriptionService;

    private ApplicationsService applicationsService;

    private TopicService topicService;

    private SubscriptionMetadata subscription = new SubscriptionMetadata();

    @BeforeEach
    void initMocks() {
        subscription.setId("sub-1");
        subscription.setTopicName("topic-1");
        subscription.setClientApplicationId("app-1");
        subscription.setState(SubscriptionState.PENDING);

        kafkaClusters = mock(KafkaClusters.class);
        subscriptionService = mock(SubscriptionService.class);
        applicationsService = mock(ApplicationsService.class);
        topicService = mock(TopicService.class);
    }

    @Test
    void testUpdateSubscription_positive() throws Exception {
        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-2");
        topic1.setSubscriptionApprovalRequired(true);

        List<InvocationOnMock> invocations = new ArrayList<>();
        when(subscriptionService.getSubscriptionsForTopic("test", "topic-1", true)).thenReturn(List.of(subscription));
        when(subscriptionService.updateSubscriptionState(any(), any(), any())).then(inv -> {
            invocations.add(inv);
            return FutureUtil.noop();
        });

        when(topicService.getTopic("test", "topic-1")).thenReturn(Optional.of(topic1));

        when(applicationsService.isUserAuthorizedFor("app-2")).thenReturn(true);
        when(applicationsService.isUserAuthorizedFor("app-1")).thenReturn(false);

        SubscriptionsController controller = new SubscriptionsController(subscriptionService, applicationsService,
                topicService, kafkaClusters);

        UpdateSubscriptionDto updateData = new UpdateSubscriptionDto();
        updateData.setNewState(SubscriptionState.APPROVED);

        controller.updateApplicationSubscription("test", "topic-1", "sub-1", updateData);
        assertEquals(1, invocations.size());
        assertEquals(SubscriptionState.APPROVED, invocations.get(0).getArgument(2));
    }

    @Test
    void testUpdateSubscription_invalidUser_clientAppOwner() throws Exception {
        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("app-2");

        when(subscriptionService.getSubscriptionsForTopic("test", "topic-1", true)).thenReturn(List.of(subscription));
        when(subscriptionService.updateSubscriptionState(any(), any(), any()))
                .thenThrow(UnsupportedOperationException.class);

        when(topicService.getTopic("test", "topic-1")).thenReturn(Optional.of(topic));

        when(applicationsService.isUserAuthorizedFor("app-1")).thenReturn(true);
        when(applicationsService.isUserAuthorizedFor("app-2")).thenReturn(false);

        SubscriptionsController controller = new SubscriptionsController(subscriptionService, applicationsService,
                topicService, kafkaClusters);

        UpdateSubscriptionDto updateData = new UpdateSubscriptionDto();
        updateData.setNewState(SubscriptionState.APPROVED);

        try {
            controller.updateApplicationSubscription("test", "topic-1", "sub-1", updateData);
            fail("Expected FORBIDDEN for owner of CLIENT application");
        }
        catch (ResponseStatusException e) {
            assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        }
    }

    @Test
    void testUpdateSubscription_invalidTopic_forSubscription() throws Exception {
        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-2");
        TopicMetadata topic2 = new TopicMetadata();
        topic2.setName("topic-2");
        topic2.setOwnerApplicationId("app-2");

        when(subscriptionService.getSubscriptionsForTopic("test", "topic-1", true)).thenReturn(List.of(subscription));
        when(subscriptionService.updateSubscriptionState(any(), any(), any()))
                .thenThrow(UnsupportedOperationException.class);

        when(topicService.getTopic("test", "topic-1")).thenReturn(Optional.of(topic1));
        when(topicService.getTopic("test", "topic-2")).thenReturn(Optional.of(topic2));

        when(applicationsService.isUserAuthorizedFor("app-2")).thenReturn(true);

        SubscriptionsController controller = new SubscriptionsController(subscriptionService, applicationsService,
                topicService, kafkaClusters);

        UpdateSubscriptionDto updateData = new UpdateSubscriptionDto();
        updateData.setNewState(SubscriptionState.APPROVED);

        try {
            controller.updateApplicationSubscription("test", "topic-2", "sub-1", updateData);
            fail("Expected NOT_FOUND as subscription does not match topic");
        }
        catch (ResponseStatusException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
    }

    @Test
    void testUpdateSubscription_topicHasFlagNotSet() throws Exception {
        TopicMetadata topic1 = new TopicMetadata();
        topic1.setName("topic-1");
        topic1.setOwnerApplicationId("app-2");
        topic1.setSubscriptionApprovalRequired(false);

        when(subscriptionService.getSubscriptionsForTopic("test", "topic-1", true)).thenReturn(List.of(subscription));
        when(topicService.getTopic("test", "topic-1")).thenReturn(Optional.of(topic1));

        when(applicationsService.isUserAuthorizedFor("app-2")).thenReturn(true);

        SubscriptionsController controller = new SubscriptionsController(subscriptionService, applicationsService,
                topicService, kafkaClusters);

        UpdateSubscriptionDto updateData = new UpdateSubscriptionDto();
        updateData.setNewState(SubscriptionState.APPROVED);

        try {
            controller.updateApplicationSubscription("test", "topic-1", "sub-1", updateData);
            fail("Expected BAD_REQUEST for updating subscription state for topic which does not require subscription approval");
        }
        catch (ResponseStatusException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }
    }

}
