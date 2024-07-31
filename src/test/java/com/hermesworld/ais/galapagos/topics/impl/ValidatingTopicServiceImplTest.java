package com.hermesworld.ais.galapagos.topics.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.messages.MessagesServiceFactory;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.config.GalapagosTopicConfig;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import com.hermesworld.ais.galapagos.topics.service.impl.ValidatingTopicServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidatingTopicServiceImplTest {

    private GalapagosTopicConfig topicConfig;

    private final MessagesServiceFactory messagesServiceFactory = new MessagesServiceFactory();

    @BeforeEach
    void init() {
        topicConfig = mock(GalapagosTopicConfig.class);
        when(topicConfig.getMinDeprecationTime()).thenReturn(Period.ofDays(10));
    }

    @Test
    void testCannotDeleteSubscribedTopic() {
        TopicService topicService = mock(TopicService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        KafkaClusters clusters = mock(KafkaClusters.class);

        TopicMetadata meta1 = new TopicMetadata();
        meta1.setName("testtopic");
        meta1.setOwnerApplicationId("1");
        meta1.setType(TopicType.EVENTS);

        SubscriptionMetadata subscription = new SubscriptionMetadata();
        subscription.setId("99");
        subscription.setTopicName("testtopic");
        subscription.setClientApplicationId("2");

        when(topicService.getTopic("_env1", "testtopic")).thenReturn(Optional.of(meta1));
        when(subscriptionService.getSubscriptionsForTopic("_env1", "testtopic", false))
                .thenReturn(List.of(subscription));

        ValidatingTopicServiceImpl service = new ValidatingTopicServiceImpl(topicService, subscriptionService,
                mock(ApplicationsService.class), clusters, topicConfig, false, messagesServiceFactory);

        assertFalse(service.canDeleteTopic("_env1", "testtopic"));
    }

    @Test
    void testCannotDeleteStagedPublicTopic() {
        TopicService topicService = mock(TopicService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        KafkaClusters clusters = mock(KafkaClusters.class);

        TopicMetadata meta1 = new TopicMetadata();
        meta1.setName("testtopic");
        meta1.setOwnerApplicationId("1");
        meta1.setType(TopicType.EVENTS);
        TopicMetadata meta2 = new TopicMetadata(meta1);

        when(topicService.getTopic("_env1", "testtopic")).thenReturn(Optional.of(meta1));
        when(topicService.getTopic("_env2", "testtopic")).thenReturn(Optional.of(meta2));
        when(clusters.getEnvironmentIds()).thenReturn(List.of("_env1", "_env2"));

        ValidatingTopicServiceImpl service = new ValidatingTopicServiceImpl(topicService, subscriptionService,
                mock(ApplicationsService.class), clusters, topicConfig, false, messagesServiceFactory);

        assertFalse(service.canDeleteTopic("_env1", "testtopic"));
        assertTrue(service.canDeleteTopic("_env2", "testtopic"));
    }

    @Test
    void canDeleteTopic_internal_positiv() {

        TopicService topicService = mock(TopicService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        KafkaClusters clusters = mock(KafkaClusters.class);

        TopicMetadata meta1 = new TopicMetadata();
        meta1.setName("testtopic");
        meta1.setOwnerApplicationId("1");
        meta1.setType(TopicType.INTERNAL);
        KafkaEnvironmentConfig envMeta = mock(KafkaEnvironmentConfig.class);

        when(envMeta.isStagingOnly()).thenReturn(false);
        when(topicService.getTopic("_env1", "testtopic")).thenReturn(Optional.of(meta1));
        when(clusters.getEnvironmentIds()).thenReturn(List.of("_env1"));
        when(clusters.getEnvironmentMetadata("_env1")).thenReturn(Optional.of(envMeta));

        ValidatingTopicServiceImpl service = new ValidatingTopicServiceImpl(topicService, subscriptionService,
                mock(ApplicationsService.class), clusters, topicConfig, false, messagesServiceFactory);

        assertTrue(service.canDeleteTopic("_env1", "testtopic"));

    }

    @Test
    void canDeleteTopic_internal_negative() {

        TopicService topicService = mock(TopicService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        KafkaClusters clusters = mock(KafkaClusters.class);

        TopicMetadata meta1 = new TopicMetadata();
        meta1.setName("testtopic");
        meta1.setOwnerApplicationId("1");
        meta1.setType(TopicType.INTERNAL);
        KafkaEnvironmentConfig envMeta = mock(KafkaEnvironmentConfig.class);

        when(envMeta.isStagingOnly()).thenReturn(true);
        when(topicService.getTopic("_env1", "testtopic")).thenReturn(Optional.of(meta1));
        when(clusters.getEnvironmentIds()).thenReturn(List.of("_env1"));
        when(clusters.getEnvironmentMetadata("_env1")).thenReturn(Optional.of(envMeta));

        ValidatingTopicServiceImpl service = new ValidatingTopicServiceImpl(topicService, subscriptionService,
                mock(ApplicationsService.class), clusters, topicConfig, false, messagesServiceFactory);

        assertFalse(service.canDeleteTopic("_env1", "testtopic"));

    }

    @Test
    @DisplayName("Should throw Exception when trying to add Producer to Topic on staging-only Stage")
    void addTopicProducerOnOnlyStagingEnv_negative() {

        TopicService topicService = mock(TopicService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        KafkaClusters clusters = mock(KafkaClusters.class);

        TopicMetadata meta1 = new TopicMetadata();
        meta1.setName("testtopic");
        meta1.setOwnerApplicationId("1");
        meta1.setType(TopicType.EVENTS);
        KafkaEnvironmentConfig envMeta = mock(KafkaEnvironmentConfig.class);

        when(envMeta.isStagingOnly()).thenReturn(true);
        when(topicService.getTopic("_env1", "testtopic")).thenReturn(Optional.of(meta1));
        when(clusters.getEnvironmentIds()).thenReturn(List.of("_env1"));
        when(clusters.getEnvironmentMetadata("_env1")).thenReturn(Optional.of(envMeta));

        ValidatingTopicServiceImpl service = new ValidatingTopicServiceImpl(topicService, subscriptionService,
                mock(ApplicationsService.class), clusters, topicConfig, false, messagesServiceFactory);

        try {
            service.addTopicProducer("_env1", "testtopic", "producer1").get();
            fail("Expected exception trying to add Producer to Topic on staging-only Stage");
        }
        catch (ExecutionException | InterruptedException e) {
            assertInstanceOf(IllegalStateException.class, e.getCause());
        }
    }

    @Test
    @DisplayName("Should throw Exception when trying to delete Producer from Topic on staging-only Stage")
    void deleteProducerFromTopicOnOnlyStagingEnv_negative() {

        TopicService topicService = mock(TopicService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        KafkaClusters clusters = mock(KafkaClusters.class);

        TopicMetadata meta1 = new TopicMetadata();
        meta1.setName("testtopic");
        meta1.setOwnerApplicationId("1");
        meta1.setType(TopicType.EVENTS);
        meta1.setProducers(List.of("producer1"));
        KafkaEnvironmentConfig envMeta = mock(KafkaEnvironmentConfig.class);

        when(envMeta.isStagingOnly()).thenReturn(true);
        when(topicService.getTopic("_env1", "testtopic")).thenReturn(Optional.of(meta1));
        when(clusters.getEnvironmentIds()).thenReturn(List.of("_env1"));
        when(clusters.getEnvironmentMetadata("_env1")).thenReturn(Optional.of(envMeta));

        ValidatingTopicServiceImpl service = new ValidatingTopicServiceImpl(topicService, subscriptionService,
                mock(ApplicationsService.class), clusters, topicConfig, false, messagesServiceFactory);

        try {
            service.removeTopicProducer("_env1", "testtopic", "producer1").get();
            fail("Expected exception trying to remove Producer from Topic on staging-only Stage");
        }
        catch (ExecutionException | InterruptedException e) {
            assertInstanceOf(IllegalStateException.class, e.getCause());

        }

    }

    @Test
    void canDeleteTopic_withSubscribersAndEolDatePast() {

        TopicService topicService = mock(TopicService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        KafkaClusters clusters = mock(KafkaClusters.class);

        TopicMetadata meta1 = new TopicMetadata();
        meta1.setName("testtopic");
        meta1.setOwnerApplicationId("1");
        meta1.setDeprecated(true);
        meta1.setDeprecationText("deprecated now");
        meta1.setType(TopicType.EVENTS);
        meta1.setEolDate(LocalDate.of(2020, 9, 10));

        SubscriptionMetadata subscription = new SubscriptionMetadata();
        subscription.setId("99");
        subscription.setTopicName("testtopic");
        subscription.setClientApplicationId("2");

        when(subscriptionService.getSubscriptionsForTopic("_env1", "testtopic", false))
                .thenReturn(List.of(subscription));
        when(topicService.getTopic("_env1", "testtopic")).thenReturn(Optional.of(meta1));

        ValidatingTopicServiceImpl service = new ValidatingTopicServiceImpl(topicService, subscriptionService,
                mock(ApplicationsService.class), clusters, topicConfig, false, messagesServiceFactory);

        assertTrue(service.canDeleteTopic("_env1", "testtopic"));
    }

    @Test
    void canDeleteTopic_withSubscribersAndEolDateInFuture() {

        TopicService topicService = mock(TopicService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        KafkaClusters clusters = mock(KafkaClusters.class);

        TopicMetadata meta1 = new TopicMetadata();
        meta1.setName("testtopic");
        meta1.setOwnerApplicationId("1");
        meta1.setDeprecated(true);
        meta1.setDeprecationText("deprecated now");
        meta1.setType(TopicType.EVENTS);
        meta1.setEolDate(LocalDate.of(2999, 9, 10));

        SubscriptionMetadata subscription = new SubscriptionMetadata();
        subscription.setId("99");
        subscription.setTopicName("testtopic");
        subscription.setClientApplicationId("2");

        when(subscriptionService.getSubscriptionsForTopic("_env1", "testtopic", false))
                .thenReturn(List.of(subscription));
        when(topicService.getTopic("_env1", "testtopic")).thenReturn(Optional.of(meta1));

        ValidatingTopicServiceImpl service = new ValidatingTopicServiceImpl(topicService, subscriptionService,
                mock(ApplicationsService.class), clusters, topicConfig, false, messagesServiceFactory);

        assertFalse(service.canDeleteTopic("_env1", "testtopic"));
    }

}
