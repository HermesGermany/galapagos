package com.hermesworld.ais.galapagos.topics.controller;

import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.RequestState;
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
import com.hermesworld.ais.galapagos.topics.service.impl.TopicServiceImpl;
import com.hermesworld.ais.galapagos.topics.service.impl.ValidatingTopicServiceImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TopicControllerTest {

    @MockBean
    private KafkaClusters kafkaClusters;

    private ApplicationsService applicationsService;

    private NamingService namingService;

    private CurrentUserService userService;

    private GalapagosTopicConfig topicConfig;

    private GalapagosEventManagerMock eventManager;

    private KafkaCluster kafkaTestCluster;

    private TopicBasedRepositoryMock<TopicMetadata> topicRepository;

    @Before
    public void feedMocks() {
        kafkaClusters = mock(KafkaClusters.class);
        applicationsService = mock(ApplicationsService.class);
        namingService = mock(NamingService.class);
        userService = mock(CurrentUserService.class);
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
    public void testDontResetDeprecationWhenTopicDescChanges() throws Exception {

        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);
        ValidatingTopicServiceImpl validatingService = new ValidatingTopicServiceImpl(service, subscriptionService,
                applicationsService, kafkaClusters, topicConfig, false);

        UpdateTopicDto dto = new UpdateTopicDto(null, null, "updated description goes here", true);

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setDeprecated(true);
        topic.setEolDate(LocalDate.of(2299, 12, 4));
        topic.setDescription("this topic is not a nice one :(");
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);
        topicRepository.save(topic).get();

        ApplicationOwnerRequest req = new ApplicationOwnerRequest();
        req.setApplicationId("app-1");
        req.setState(RequestState.APPROVED);

        when(applicationsService.getUserApplicationOwnerRequests()).thenReturn((List.of(req)));

        TopicController controller = new TopicController(validatingService, kafkaClusters, applicationsService,
                namingService);

        controller.updateTopic("test", "topic-1", dto);
        TopicMetadata savedTopic = topicRepository.getObject("topic-1").get();

        assertTrue(savedTopic.isDeprecated());
    }

    @Test
    @DisplayName("it should change the owner if current user is authorized")
    public void testChangeTopicOwner_positive() throws Exception {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);
        ValidatingTopicServiceImpl validatingService = new ValidatingTopicServiceImpl(service, subscriptionService,
                applicationsService, kafkaClusters, topicConfig, false);

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);
        topicRepository.save(topic).get();

        when(applicationsService.isUserAuthorizedFor("app-1")).thenReturn(true);

        TopicController controller = new TopicController(validatingService, kafkaClusters, applicationsService,
                namingService);

        ChangeTopicOwnerDto dto = new ChangeTopicOwnerDto();
        dto.setProducerApplicationId("producer1");
        controller.changeTopicOwner("test", "topic-1", dto);
        TopicMetadata savedTopic = topicRepository.getObject("topic-1").get();

        assertEquals("producer1", savedTopic.getOwnerApplicationId());
    }

    @Test
    @DisplayName("it should not change the owner if current user is not authorized")
    public void testChangeTopicOwner_negative() throws Exception {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        TopicServiceImpl service = new TopicServiceImpl(kafkaClusters, applicationsService, namingService, userService,
                topicConfig, eventManager);
        ValidatingTopicServiceImpl validatingService = new ValidatingTopicServiceImpl(service, subscriptionService,
                applicationsService, kafkaClusters, topicConfig, false);

        TopicMetadata topic = new TopicMetadata();
        topic.setName("topic-1");
        topic.setOwnerApplicationId("app-1");
        topic.setType(TopicType.EVENTS);
        topicRepository.save(topic).get();

        ApplicationOwnerRequest req = new ApplicationOwnerRequest();
        req.setApplicationId("app-2");
        req.setState(RequestState.APPROVED);

        when(applicationsService.getUserApplicationOwnerRequests()).thenReturn((List.of(req)));

        TopicController controller = new TopicController(validatingService, kafkaClusters, applicationsService,
                namingService);

        ChangeTopicOwnerDto dto = new ChangeTopicOwnerDto();
        dto.setProducerApplicationId("producer1");

        try {
            controller.changeTopicOwner("test", "topic-1", dto);
            fail("should fail because current user is not authorized");
        }
        catch (ResponseStatusException e) {
            TopicMetadata savedTopic = topicRepository.getObject("topic-1").get();
            assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
            assertEquals("app-1", savedTopic.getOwnerApplicationId());
        }

    }

}
