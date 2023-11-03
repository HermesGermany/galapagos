package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionState;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CreateBackupJobTest {

    private KafkaClusters kafkaClusters;

    private final Map<String, TopicBasedRepositoryMock<?>> testRepo = new HashMap<>();

    private final Map<String, TopicBasedRepositoryMock<?>> prodRepo = new HashMap<>();

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        testRepo.put("topics", new TopicBasedRepositoryMock<TopicMetadata>() {
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
        testRepo.put("subscriptions", new TopicBasedRepositoryMock<SubscriptionMetadata>() {
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
        testRepo.put("application-owner-requests", new TopicBasedRepositoryMock<ApplicationOwnerRequest>() {
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

        prodRepo.put("topics", new TopicBasedRepositoryMock<TopicMetadata>() {
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
                meta1.setName("topic-2");
                meta1.setOwnerApplicationId("app-2");
                meta1.setType(TopicType.EVENTS);
                return List.of(meta1);
            }

        });
        prodRepo.put("subscriptions", new TopicBasedRepositoryMock<SubscriptionMetadata>() {
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
                sub1.setId("12323");
                sub1.setClientApplicationId("app-12");
                sub1.setTopicName("topic-2");
                sub1.setState(SubscriptionState.APPROVED);
                return List.of(sub1);
            }

        });
        prodRepo.put("application-owner-requests", new TopicBasedRepositoryMock<ApplicationOwnerRequest>() {
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
                req.setApplicationId("app-2");
                req.setId("2");
                req.setUserName("myUser2");
                req.setState(RequestState.APPROVED);
                return List.of(req);
            }

        });

        mapper = JsonUtil.newObjectMapper();
        kafkaClusters = mock(KafkaClusters.class);
        KafkaCluster testCluster = mock(KafkaCluster.class);
        KafkaCluster prodCluster = mock(KafkaCluster.class);
        when(testCluster.getId()).thenReturn("test");
        when(prodCluster.getId()).thenReturn("prod");
        doReturn(testRepo.values()).when(testCluster).getRepositories();
        doReturn(prodRepo.values()).when(prodCluster).getRepositories();
        // noinspection SuspiciousMethodCalls
        when(testCluster.getRepository(any(), any())).then(inv -> testRepo.get(inv.getArgument(0)));
        // noinspection SuspiciousMethodCalls
        when(prodCluster.getRepository(any(), any())).then(inv -> prodRepo.get(inv.getArgument(0)));
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
        when(kafkaClusters.getEnvironment("prod")).thenReturn(Optional.of(prodCluster));
        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("test", "prod"));
    }

    @Test
    @DisplayName("it should create a backup from all the metadata currently saved within Galapagos")
    void createBackUp_success() throws Exception {
        CreateBackupJob job = new CreateBackupJob(kafkaClusters);
        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("create.backup.file")).thenReturn(List.of("true"));

        try {
            job.run(args);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        String backUpJson = Files.readString(Path.of("backup.json"));

        JsonNode jsonNode = mapper.readTree(backUpJson);
        String topicName = jsonNode.get("test").get("topics").get("topic-1").get("name").toString();
        String topicType = jsonNode.get("test").get("topics").get("topic-1").get("type").toString();
        String clientApplicationIdSub = jsonNode.get("test").get("subscriptions").get("123").get("clientApplicationId")
                .toString();
        String subId = jsonNode.get("test").get("subscriptions").get("123").get("id").toString();
        String aorId = jsonNode.get("test").get("application-owner-requests").get("1").get("id").toString();
        String aorState = jsonNode.get("test").get("application-owner-requests").get("1").get("state").toString();
        String username = jsonNode.get("test").get("application-owner-requests").get("1").get("userName").toString();

        String topicNameProd = jsonNode.get("prod").get("topics").get("topic-2").get("name").toString();
        String topicTypeProd = jsonNode.get("prod").get("topics").get("topic-2").get("type").toString();
        String clientApplicationIdSubProd = jsonNode.get("prod").get("subscriptions").get("12323")
                .get("clientApplicationId").toString();
        String subIdProd = jsonNode.get("prod").get("subscriptions").get("12323").get("id").toString();
        String aorIdProd = jsonNode.get("prod").get("application-owner-requests").get("2").get("id").toString();
        String aorStateProd = jsonNode.get("prod").get("application-owner-requests").get("2").get("state").toString();
        String usernameProd = jsonNode.get("prod").get("application-owner-requests").get("2").get("userName")
                .toString();

        // test data
        assertEquals("\"topic-1\"", topicName);
        assertEquals("\"EVENTS\"", topicType);
        assertEquals("\"app-1\"", clientApplicationIdSub);
        assertEquals("\"123\"", subId);
        assertEquals("\"1\"", aorId);
        assertEquals("\"APPROVED\"", aorState);
        assertEquals("\"myUser\"", username);
        // prod data
        assertEquals("\"topic-2\"", topicNameProd);
        assertEquals("\"EVENTS\"", topicTypeProd);
        assertEquals("\"app-12\"", clientApplicationIdSubProd);
        assertEquals("\"12323\"", subIdProd);
        assertEquals("\"2\"", aorIdProd);
        assertEquals("\"APPROVED\"", aorStateProd);
        assertEquals("\"myUser2\"", usernameProd);

    }

}
