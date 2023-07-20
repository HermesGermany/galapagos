package com.hermesworld.ais.galapagos.topics.service.impl;

import com.hermesworld.ais.galapagos.GalapagosTestConfig;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.changes.ChangeData;
import com.hermesworld.ais.galapagos.events.*;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.notifications.NotificationService;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.config.GalapagosTopicConfig;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.HasKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that Security Context data is passed correctly to event context, even when performing the quite complex,
 * chained <code>markTopicDeprecated</code> and <code>unmarkTopicDeprecated</code> operations.
 */
@SpringBootTest
@Import({ GalapagosTestConfig.class, TopicServiceImplIntegrationTest.TestEventListener.class })
class TopicServiceImplIntegrationTest {

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private GalapagosEventManager eventManager;

    @Autowired
    private TestEventListener eventListener;

    @MockBean
    private KafkaClusters clusters;

    @MockBean
    private NotificationService notificationService;

    private final AtomicInteger threadNo = new AtomicInteger();

    private final ExecutorService executorService = Executors
            .newCachedThreadPool(r -> new Thread(r, "inttest-" + threadNo.incrementAndGet()));

    private final TopicBasedRepositoryMock<TopicMetadata> topicRepository1 = new DecoupledTopicBasedRepositoryMock<>(
            executorService);

    private final TopicBasedRepositoryMock<TopicMetadata> topicRepository2 = new DecoupledTopicBasedRepositoryMock<>(
            executorService);

    @BeforeEach
    void feedMocks() {
        KafkaCluster cluster1 = mock(KafkaCluster.class);
        KafkaCluster cluster2 = mock(KafkaCluster.class);

        when(cluster1.getId()).thenReturn("dev");
        when(cluster2.getId()).thenReturn("int");

        when(cluster1.getRepository("topics", TopicMetadata.class)).thenReturn(topicRepository1);
        when(cluster2.getRepository("topics", TopicMetadata.class)).thenReturn(topicRepository2);

        when(cluster1.getRepository("changelog", ChangeData.class))
                .thenReturn(new DecoupledTopicBasedRepositoryMock<>(executorService));
        when(cluster2.getRepository("changelog", ChangeData.class))
                .thenReturn(new DecoupledTopicBasedRepositoryMock<>(executorService));

        when(clusters.getEnvironmentIds()).thenReturn(List.of("dev", "int"));
        when(clusters.getProductionEnvironmentId()).thenReturn("int");

        when(clusters.getEnvironment("dev")).thenReturn(Optional.of(cluster1));
        when(clusters.getEnvironment("int")).thenReturn(Optional.of(cluster2));

        when(notificationService.notifySubscribers(any(), any(), any(), any())).thenReturn(FutureUtil.noop());
    }

    @AfterEach
    void shutdown() throws Exception {
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(1, TimeUnit.MINUTES));
    }

    @Test
    void testTopicDeprecated_passesCurrentUser() throws Exception {
        ApplicationsService applicationsService = mock(ApplicationsService.class);
        NamingService namingService = mock(NamingService.class);
        GalapagosTopicConfig topicSettings = mock(GalapagosTopicConfig.class);

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);
        topicRepository1.save(topic).get();
        topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);
        topicRepository2.save(topic).get();

        TopicServiceImpl service = new TopicServiceImpl(clusters, applicationsService, namingService,
                currentUserService, topicSettings, eventManager);

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);

        when(auth.getPrincipal()).thenReturn(new Object());
        when(auth.getName()).thenReturn("testUser");
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        service.markTopicDeprecated("topic-1", "deprecated", LocalDate.of(2999, 1, 1)).get();

        assertEquals(2, eventListener.deprecationEvents.size());
        assertEquals("testUser",
                eventListener.deprecationEvents.get(1).getContext().getContextValue("username").orElse(null));
    }

    @Test
    void testTopicUndeprecated_passesCurrentUser() throws Exception {
        ApplicationsService applicationsService = mock(ApplicationsService.class);
        NamingService namingService = mock(NamingService.class);
        GalapagosTopicConfig topicSettings = mock(GalapagosTopicConfig.class);

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);
        topic.setDeprecated(true);
        topicRepository1.save(topic).get();
        topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);
        topic.setDeprecated(true);
        topicRepository2.save(topic).get();

        TopicServiceImpl service = new TopicServiceImpl(clusters, applicationsService, namingService,
                currentUserService, topicSettings, eventManager);

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);

        when(auth.getPrincipal()).thenReturn(new Object());
        when(auth.getName()).thenReturn("testUser");
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        service.unmarkTopicDeprecated("topic-1").get();

        assertEquals(2, eventListener.undeprecationEvents.size());
        assertEquals("testUser",
                eventListener.undeprecationEvents.get(1).getContext().getContextValue("username").orElse(null));
    }

    @Component
    public static class TestEventListener implements TopicEventsListener {

        private final List<TopicEvent> deprecationEvents = new ArrayList<>();

        private final List<TopicEvent> undeprecationEvents = new ArrayList<>();

        @Override
        public CompletableFuture<Void> handleTopicCreated(TopicCreatedEvent event) {
            throw new UnsupportedOperationException("Unexpected event received during test");
        }

        @Override
        public CompletableFuture<Void> handleTopicDeleted(TopicEvent event) {
            throw new UnsupportedOperationException("Unexpected event received during test");
        }

        @Override
        public CompletableFuture<Void> handleTopicDescriptionChanged(TopicEvent event) {
            throw new UnsupportedOperationException("Unexpected event received during test");
        }

        @Override
        public CompletableFuture<Void> handleTopicDeprecated(TopicEvent event) {
            deprecationEvents.add(event);
            return FutureUtil.noop();
        }

        @Override
        public CompletableFuture<Void> handleTopicUndeprecated(TopicEvent event) {
            undeprecationEvents.add(event);
            return FutureUtil.noop();
        }

        @Override
        public CompletableFuture<Void> handleTopicSchemaAdded(TopicSchemaAddedEvent event) {
            throw new UnsupportedOperationException("Unexpected event received during test");
        }

        @Override
        public CompletableFuture<Void> handleTopicSchemaDeleted(TopicSchemaRemovedEvent event) {
            throw new UnsupportedOperationException("Unexpected event received during test");
        }

        @Override
        public CompletableFuture<Void> handleTopicSubscriptionApprovalRequiredFlagChanged(TopicEvent event) {
            throw new UnsupportedOperationException("Unexpected event received during test");
        }

        @Override
        public CompletableFuture<Void> handleAddTopicProducer(TopicAddProducerEvent event) {
            throw new UnsupportedOperationException("Unexpected event received during test");
        }

        @Override
        public CompletableFuture<Void> handleRemoveTopicProducer(TopicRemoveProducerEvent event) {
            throw new UnsupportedOperationException("Unexpected event received during test");
        }

        @Override
        public CompletableFuture<Void> handleTopicOwnerChanged(TopicOwnerChangeEvent event) {
            throw new UnsupportedOperationException("Unexpected event received during test");
        }
    }

    /**
     * A mock implementation for a TopicBasedRepository which behaves more like the original, Kafka-based one, in that
     * it completes each save operation asynchronously, on a new Thread.
     *
     * @param <T> Type of the elements stored in the repository.
     */
    private static class DecoupledTopicBasedRepositoryMock<T extends HasKey> extends TopicBasedRepositoryMock<T> {

        private final ExecutorService executorService;

        public DecoupledTopicBasedRepositoryMock(ExecutorService executorService) {
            this.executorService = executorService;
        }

        @Override
        public CompletableFuture<Void> save(T value) {
            return super.save(value).thenCompose(o -> CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(200);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executorService));
        }
    }

}
