package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionState;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CreateBackUpJobTest {

    private KafkaClusters kafkaClusters;

    private final Map<String, TopicBasedRepositoryMock<?>> repositories = new ConcurrentHashMap<>();

    private ObjectMapper mapper;

    @Before
    public void setUp() {
        repositories.put("topics", new TopicBasedRepositoryMock<TopicMetadata>() {
            @Override
            public Class<TopicMetadata> getValueClass() {
                return TopicMetadata.class;
            }

            @Override
            public String getTopicName() {
                return "topics";
            }

            @Override
            public Collection<TopicMetadata> getObjects() {
                TopicMetadata meta1 = new TopicMetadata();
                meta1.setName("topic-1");
                meta1.setOwnerApplicationId("app-1");
                meta1.setType(TopicType.EVENTS);
                return List.of(meta1);
            }

        });
        repositories.put("subscriptions", new TopicBasedRepositoryMock<SubscriptionMetadata>() {
            @Override
            public Class<SubscriptionMetadata> getValueClass() {
                return SubscriptionMetadata.class;
            }

            @Override
            public String getTopicName() {
                return "subscriptions";
            }

            @Override
            public Collection<SubscriptionMetadata> getObjects() {
                SubscriptionMetadata sub1 = new SubscriptionMetadata();
                sub1.setId("123");
                sub1.setClientApplicationId("app-1");
                sub1.setTopicName("topic-1");
                sub1.setState(SubscriptionState.APPROVED);
                return List.of(sub1);
            }

        });
        repositories.put("application-owner-requests", new TopicBasedRepositoryMock<ApplicationOwnerRequest>() {
            @Override
            public Class<ApplicationOwnerRequest> getValueClass() {
                return ApplicationOwnerRequest.class;
            }

            @Override
            public String getTopicName() {
                return "application-owner-requests";
            }

            @Override
            public Collection<ApplicationOwnerRequest> getObjects() {
                ApplicationOwnerRequest req = new ApplicationOwnerRequest();
                req.setApplicationId("app-1");
                req.setId("1");
                req.setUserName("myUser");
                req.setState(RequestState.APPROVED);
                return List.of(req);
            }

        });

        mapper = JsonUtil.newObjectMapper();
        kafkaClusters = mock(KafkaClusters.class);
        KafkaCluster testCluster = mock(KafkaCluster.class);
        when(testCluster.getId()).thenReturn("test");
        when(testCluster.getRepositories()).thenReturn(new HashSet<>(repositories.values()));
        when(testCluster.getRepository("topics", TopicMetadata.class))
                .thenReturn((TopicBasedRepository<TopicMetadata>) repositories.get("topics"));
        when(testCluster.getRepository("subscriptions", SubscriptionMetadata.class))
                .thenReturn((TopicBasedRepository<SubscriptionMetadata>) repositories.get("subscriptions"));
        when(testCluster.getRepository("application-owner-requests", ApplicationOwnerRequest.class)).thenReturn(
                (TopicBasedRepository<ApplicationOwnerRequest>) repositories.get("application-owner-requests"));
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("test"));
    }

    @Test
    @DisplayName("it should create a backup from all the metadata currently saved within Galapagos")
    public void createBackUp_success() throws Exception {
        CreateBackUpJob job = new CreateBackUpJob(kafkaClusters);
        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("create.backup.file")).thenReturn(Collections.singletonList("true"));

        try {
            job.run(args);

        }
        catch (Exception e) {
            e.printStackTrace();
        }

        String backUpJson = StreamUtils.copyToString(new ClassPathResource("backup.json").getInputStream(),
                Charset.defaultCharset());

        JsonNode jsonNode = mapper.readTree(backUpJson);
        String topicName = jsonNode.get("test").get("topics").get("topic-1").get("name").toString();
        String topicType = jsonNode.get("test").get("topics").get("topic-1").get("type").toString();
        String clientApplicationIdSub = jsonNode.get("test").get("subscriptions").get("123").get("clientApplicationId")
                .toString();
        String subId = jsonNode.get("test").get("subscriptions").get("123").get("id").toString();
        String aorId = jsonNode.get("test").get("application-owner-requests").get("1").get("id").toString();
        String aorState = jsonNode.get("test").get("application-owner-requests").get("1").get("state").toString();
        String username = jsonNode.get("test").get("application-owner-requests").get("1").get("userName").toString();

        Assertions.assertEquals(topicName, "\"topic-1\"");
        Assertions.assertEquals(topicType, "\"EVENTS\"");
        Assertions.assertEquals(clientApplicationIdSub, "\"app-1\"");
        Assertions.assertEquals(subId, "\"123\"");
        Assertions.assertEquals(aorId, "\"1\"");
        Assertions.assertEquals(aorState, "\"APPROVED\"");
        Assertions.assertEquals(username, "\"myUser\"");

    }

}
