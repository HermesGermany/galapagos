package com.hermesworld.ais.galapagos.topics.controller;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.events.GalapagosEventManagerMock;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.config.GalapagosTopicConfig;
import com.hermesworld.ais.galapagos.topics.service.ValidatingTopicService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

class TopicControllerTest {

    @MockBean
    private KafkaClusters kafkaClusters;

    private ApplicationsService applicationsService;

    private NamingService namingService;

    private CurrentUserService userService;

    private SubscriptionService subscriptionService;

    private GalapagosTopicConfig topicConfig;

    private GalapagosEventManagerMock eventManager;

    private KafkaCluster kafkaTestCluster;

    private TopicBasedRepositoryMock<TopicMetadata> topicRepository;

    @BeforeEach
    void feedMocks() {
        kafkaClusters = mock(KafkaClusters.class);
        applicationsService = mock(ApplicationsService.class);
        namingService = mock(NamingService.class);
        userService = mock(CurrentUserService.class);
        subscriptionService = mock(SubscriptionService.class);
        topicConfig = mock(GalapagosTopicConfig.class);
        eventManager = new GalapagosEventManagerMock();
        kafkaTestCluster = mock(KafkaCluster.class);
        topicRepository = new TopicBasedRepositoryMock<>();

        when(kafkaTestCluster.getId()).thenReturn("test");
        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("test"));
        when(kafkaTestCluster.getRepository("topics", TopicMetadata.class)).thenReturn(topicRepository);
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(kafkaTestCluster));
    }

    @Test
    @DisplayName("it should not change the deprecation if topic description is changed")
    void testDontResetDeprecationWhenTopicDescChanges() {
        TopicMetadata topic = new TopicMetadata();
        topic.setOwnerApplicationId("app-1");
        topic.setName("topic-1");
        topic.setDeprecated(true);
        topic.setEolDate(LocalDate.of(2299, 12, 4));
        topic.setDescription("this topic is not a nice one :(");

        ValidatingTopicService topicService = mock(ValidatingTopicService.class);
        when(topicService.getTopic("test", "topic-1")).thenReturn(Optional.of(topic));
        UpdateTopicDto dto = new UpdateTopicDto(null, null, "updated description goes here", true);

        when(topicService.updateTopicDescription("test", "topic-1", "updated description goes here"))
                .thenReturn(FutureUtil.noop());
        when(applicationsService.isUserAuthorizedFor("app-1")).thenReturn(true);
        TopicController controller = new TopicController(topicService, kafkaClusters, applicationsService,
                namingService, userService);

        controller.updateTopic("test", "topic-1", dto);
        verify(topicService, times(1)).updateTopicDescription("test", "topic-1", "updated description goes here");
        verify(topicService, times(0)).unmarkTopicDeprecated(nullable(String.class));
    }

    @Test
    @DisplayName("it should change the owner if current user is authorized")
    void testChangeTopicOwner_positive() throws Exception {
        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("app-1");

        ValidatingTopicService topicService = mock(ValidatingTopicService.class);
        when(topicService.getTopic("test", "topic-1")).thenReturn(Optional.of(topic));

        when(applicationsService.isUserAuthorizedFor("app-1")).thenReturn(true);

        TopicController controller = new TopicController(topicService, kafkaClusters, applicationsService,
                namingService, userService);
        when(topicService.changeTopicOwner("test", "topic-1", "producer1")).thenReturn(FutureUtil.noop());

        ChangeTopicOwnerDto dto = new ChangeTopicOwnerDto();
        dto.setProducerApplicationId("producer1");
        controller.changeTopicOwner("test", "topic-1", dto);

        verify(topicService, times(1)).changeTopicOwner("test", "topic-1", "producer1");
    }

    @Test
    @DisplayName("it should not change the owner if current user is not authorized")
    void testChangeTopicOwner_negative() {
        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);

        ValidatingTopicService topicService = mock(ValidatingTopicService.class);
        when(topicService.getTopic("test", "topic-1")).thenReturn(Optional.of(topic));

        TopicController controller = new TopicController(topicService, kafkaClusters, applicationsService,
                namingService, userService);

        ChangeTopicOwnerDto dto = new ChangeTopicOwnerDto();
        dto.setProducerApplicationId("producer1");

        try {
            controller.changeTopicOwner("test", "topic-1", dto);
            fail("should fail because current user is not authorized");
        }
        catch (ResponseStatusException e) {
            assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        }
    }

    @Test
    @DisplayName("Can add producers for which I am not authorized")
    void testAddTopicProducer_notAuthorizedForProducer_positive() {
        ValidatingTopicService topicService = mock(ValidatingTopicService.class);

        TopicController controller = new TopicController(topicService, kafkaClusters, applicationsService,
                namingService, userService);

        // WHEN I am authorized for the topic owning application, but not the producer application
        when(applicationsService.isUserAuthorizedFor("app-1")).thenReturn(true);
        when(applicationsService.isUserAuthorizedFor("app-9")).thenReturn(false);

        TopicMetadata metadata = new TopicMetadata();
        metadata.setName("testtopic");
        metadata.setOwnerApplicationId("app-1");
        when(topicService.getTopic("test", "testtopic")).thenReturn(Optional.of(metadata));

        when(topicService.addTopicProducer("test", "testtopic", "app-9")).thenReturn(FutureUtil.noop());

        // THEN adding the producer must still succeed
        AddProducerDto producerDto = new AddProducerDto();
        producerDto.setProducerApplicationId("app-9");
        controller.addProducerToTopic("test", "testtopic", producerDto);

        verify(topicService, times(1)).addTopicProducer("test", "testtopic", "app-9");
    }

    @Test
    @DisplayName("Cannot add producer if not authorized for topic (but for producer)")
    void testAddTopicProducer_notAuthorizedForTopic_negative() {
        ValidatingTopicService topicService = mock(ValidatingTopicService.class);

        TopicController controller = new TopicController(topicService, kafkaClusters, applicationsService,
                namingService, userService);

        // WHEN I am authorized for the producer, but not the topic owning application
        when(applicationsService.isUserAuthorizedFor("app-9")).thenReturn(true);
        when(applicationsService.isUserAuthorizedFor("app-1")).thenReturn(false);

        TopicMetadata metadata = new TopicMetadata();
        metadata.setName("testtopic");
        metadata.setOwnerApplicationId("app-1");
        when(topicService.getTopic("test", "testtopic")).thenReturn(Optional.of(metadata));

        when(topicService.addTopicProducer("test", "testtopic", "app-9")).thenReturn(FutureUtil.noop());

        // THEN adding the producer must fail
        AddProducerDto producerDto = new AddProducerDto();
        producerDto.setProducerApplicationId("app-9");

        try {
            controller.addProducerToTopic("test", "testtopic", producerDto);
            fail("ResponseStatusException expected, but adding producer succeeded");
        }
        catch (ResponseStatusException e) {
            assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        }
    }

    @Test
    @DisplayName("Can remove producers for which I am not authorized")
    void testRemoveTopicProducer_notAuthorizedForProducer_positive() {
        ValidatingTopicService topicService = mock(ValidatingTopicService.class);

        TopicController controller = new TopicController(topicService, kafkaClusters, applicationsService,
                namingService, userService);

        // WHEN I am authorized for the topic owning application, but not the producer application
        when(applicationsService.isUserAuthorizedFor("app-1")).thenReturn(true);
        when(applicationsService.isUserAuthorizedFor("app-9")).thenReturn(false);

        TopicMetadata metadata = new TopicMetadata();
        metadata.setName("testtopic");
        metadata.setOwnerApplicationId("app-1");
        when(topicService.getTopic("test", "testtopic")).thenReturn(Optional.of(metadata));

        when(topicService.removeTopicProducer("test", "testtopic", "app-9")).thenReturn(FutureUtil.noop());

        // THEN adding the producer must still succeed
        controller.removeProducerFromTopic("test", "testtopic", "app-9");

        verify(topicService, times(1)).removeTopicProducer("test", "testtopic", "app-9");
    }

    @Test
    @DisplayName("user cant skip compability check if not admin")
    void testSkipCombatCheckForSchemas_userNotAuthorized() {
        ValidatingTopicService topicService = mock(ValidatingTopicService.class);

        TopicController controller = new TopicController(topicService, kafkaClusters, applicationsService,
                namingService, userService);

        AddSchemaVersionDto dto = new AddSchemaVersionDto();
        dto.setJsonSchema(new JSONObject(Map.of("a", "1", "b", "2")).toString());

        TopicMetadata metadata = new TopicMetadata();
        metadata.setName("testtopic");
        metadata.setOwnerApplicationId("app-1");
        when(topicService.listTopics("test")).thenReturn(List.of(metadata));
        when(userService.isAdmin()).thenReturn(false);
        when(applicationsService.isUserAuthorizedFor("app-1")).thenReturn(true);

        try {
            controller.addTopicSchemaVersion("test", "testtopic", true, dto);
            fail("HttpStatus.FORBIDDEN expected, but skipping check succeeded");
        }
        catch (ResponseStatusException e) {
            assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        }
    }

    @Test
    @DisplayName("Cannot remove producer if not authorized for topic (but for producer)")
    void testRemoveTopicProducer_notAuthorizedForTopic_negative() {
        ValidatingTopicService topicService = mock(ValidatingTopicService.class);

        TopicController controller = new TopicController(topicService, kafkaClusters, applicationsService,
                namingService, userService);

        // WHEN I am authorized for the producer, but not the topic owning application
        when(applicationsService.isUserAuthorizedFor("app-9")).thenReturn(true);
        when(applicationsService.isUserAuthorizedFor("app-1")).thenReturn(false);

        TopicMetadata metadata = new TopicMetadata();
        metadata.setName("testtopic");
        metadata.setOwnerApplicationId("app-1");
        when(topicService.getTopic("test", "testtopic")).thenReturn(Optional.of(metadata));

        when(topicService.removeTopicProducer("test", "testtopic", "app-9")).thenReturn(FutureUtil.noop());

        // THEN removing the producer must fail
        try {
            controller.removeProducerFromTopic("test", "testtopic", "app-9");
            fail("ResponseStatusException expected, but removing producer succeeded");
        }
        catch (ResponseStatusException e) {
            assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        }
    }

}
