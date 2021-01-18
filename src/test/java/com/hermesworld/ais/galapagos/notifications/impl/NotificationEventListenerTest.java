package com.hermesworld.ais.galapagos.notifications.impl;

import java.util.concurrent.atomic.AtomicInteger;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.events.GalapagosEventContext;
import com.hermesworld.ais.galapagos.events.TopicEvent;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.notifications.NotificationService;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NotificationEventListenerTest {

    private NotificationEventListener listener;

    private NotificationService notificationService;

    @Before
    public void feedMocks() {
        notificationService = mock(NotificationService.class);

        KafkaClusters kafkaClusters = mock(KafkaClusters.class);
        ApplicationsService applicationsService = mock(ApplicationsService.class);
        TopicService topicService = mock(TopicService.class);
        CurrentUserService userService = mock(CurrentUserService.class);

        when(kafkaClusters.getProductionEnvironmentId()).thenReturn("prod");

        listener = new NotificationEventListener(notificationService, applicationsService, topicService, userService,
                kafkaClusters);
    }

    @Test
    public void testHandleTopicDeprecated() {
        AtomicInteger sendCalled = new AtomicInteger();
        when(notificationService.notifySubscribers(any(), any(), any(), any())).then(inv -> {
            sendCalled.incrementAndGet();
            return FutureUtil.noop();
        });

        listener.handleTopicDeprecated(buildTestEvent("test1"));
        listener.handleTopicDeprecated(buildTestEvent("test2"));
        listener.handleTopicDeprecated(buildTestEvent("prod"));

        assertEquals("Deprecation mail should only be sent for production environment", 1, sendCalled.get());
    }

    @Test
    public void testHandleTopicUndeprecated() {
        AtomicInteger sendCalled = new AtomicInteger();
        when(notificationService.notifySubscribers(any(), any(), any(), any())).then(inv -> {
            sendCalled.incrementAndGet();
            return FutureUtil.noop();
        });

        listener.handleTopicUndeprecated(buildTestEvent("test1"));
        listener.handleTopicUndeprecated(buildTestEvent("test2"));
        listener.handleTopicUndeprecated(buildTestEvent("prod"));

        assertEquals("Undeprecation mail should only be sent for production environment", 1, sendCalled.get());
    }

    private TopicEvent buildTestEvent(String envId) {
        GalapagosEventContext context = mock(GalapagosEventContext.class);
        KafkaCluster cluster = mock(KafkaCluster.class);
        when(cluster.getId()).thenReturn(envId);
        when(context.getKafkaCluster()).thenReturn(cluster);

        TopicMetadata metadata = new TopicMetadata();
        metadata.setName("topic-1");
        metadata.setType(TopicType.EVENTS);

        return new TopicEvent(context, metadata);
    }

}
