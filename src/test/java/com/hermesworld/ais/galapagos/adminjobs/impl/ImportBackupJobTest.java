package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportBackupJobTest {

    private ImportBackupJob job;

    private KafkaClusters kafkaClusters;

    private KafkaCluster testCluster;

    private final File testFile = new File("src/test/resources/backup-test.json");

    private TopicBasedRepositoryMock<TopicMetadata> topicRepository;

    @BeforeEach
    void setUp() {
        kafkaClusters = mock(KafkaClusters.class);
        testCluster = mock(KafkaCluster.class);
        when(testCluster.getId()).thenReturn("prod");
        when(kafkaClusters.getEnvironment("prod")).thenReturn(Optional.of(testCluster));
        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("prod"));
        job = new ImportBackupJob(kafkaClusters);
        topicRepository = new TopicBasedRepositoryMock<>() {
            @Override
            public Class<TopicMetadata> getValueClass() {
                return TopicMetadata.class;
            }

            @Override
            public String getTopicName() {
                return "topics";
            }
        };
    }

    @Test
    @DisplayName("should import backup from import file")
    void importBackupTest() throws Exception {
        // given
        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("import.file")).thenReturn(List.of(testFile.getPath()));
        when(args.getOptionValues("clearRepos")).thenReturn(List.of("false"));
        when(testCluster.getRepositories()).thenReturn(List.of(topicRepository));
        // when
        job.run(args);

        // then
        assertTrue(topicRepository.getObject("de.hlg.events.sales.my-topic-nice").isPresent());
    }

    @Test
    @DisplayName("should not clear non-imported environments")
    void importBackup_noClearOnOtherEnv() throws Exception {
        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("import.file")).thenReturn(List.of(testFile.getPath()));
        when(args.getOptionValues("clearRepos")).thenReturn(List.of("true"));
        when(testCluster.getRepositories()).thenReturn(List.of(topicRepository));

        KafkaCluster devCluster = mock(KafkaCluster.class);
        when(devCluster.getId()).thenReturn("dev");
        TopicBasedRepositoryMock<TopicMetadata> devRepository = new TopicBasedRepositoryMock<>() {
            @Override
            public Class<TopicMetadata> getValueClass() {
                return TopicMetadata.class;
            }

            @Override
            public String getTopicName() {
                return "topics";
            }

            @Override
            public CompletableFuture<Void> delete(TopicMetadata value) {
                return CompletableFuture.failedFuture(
                        new UnsupportedOperationException("Should not call delete() on dev environment during import"));
            }
        };
        TopicMetadata meta = new TopicMetadata();
        meta.setName("devtopic");
        devRepository.save(meta).get();
        when(devCluster.getRepositories()).thenReturn(List.of(devRepository));

        when(kafkaClusters.getEnvironment("dev")).thenReturn(Optional.of(devCluster));
        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("dev", "prod"));

        job.run(args);
    }

    @Test
    @DisplayName("should import backup from import file and old metadata in repos should still be present")
    void importBackupWithoutClearingExistingRepos() throws Exception {
        // given
        ApplicationArguments args = mock(ApplicationArguments.class);
        topicRepository.save(buildTopicMetadata()).get();
        // when
        when(args.getOptionValues("import.file")).thenReturn(List.of(testFile.getPath()));
        when(args.getOptionValues("clearRepos")).thenReturn(List.of("false"));
        when(testCluster.getRepositories()).thenReturn(List.of(topicRepository));

        // then
        job.run(args);
        assertTrue(topicRepository.getObject("de.hlg.events.sales.my-topic-nice").isPresent());
        assertEquals(2, topicRepository.getObjects().size());

    }

    @Test
    @DisplayName("should import backup from import file and old metadata in repos should be not present")
    void importBackupWithClearingExistingRepos() throws Exception {
        // given
        ApplicationArguments args = mock(ApplicationArguments.class);
        topicRepository.save(buildTopicMetadata()).get();
        // when
        when(args.getOptionValues("import.file")).thenReturn(List.of(testFile.getPath()));
        when(args.getOptionValues("clearRepos")).thenReturn(List.of("true"));
        when(testCluster.getRepositories()).thenReturn(List.of(topicRepository));

        // then
        job.run(args);
        assertTrue(topicRepository.getObject("de.hlg.events.sales.my-topic-nice").isPresent());
        assertEquals(1, topicRepository.getObjects().size());

    }

    @Test
    @DisplayName("should throw exception because no import file is set")
    void importBackupTest_noFileOption() throws Exception {
        // given
        ApplicationArguments args = mock(ApplicationArguments.class);
        topicRepository.save(buildTopicMetadata()).get();
        // when
        when(args.getOptionValues("clearRepos")).thenReturn(List.of("true"));
        when(testCluster.getRepositories()).thenReturn(List.of(topicRepository));

        // then
        try {
            job.run(args);
            fail("job.run() should have thrown an error since there is no import file given");
        }
        catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }

    }

    @Test
    @DisplayName("should throw exception because no clearRepos option given")
    void importBackupTest_noClearReposOption() throws Exception {
        // given
        ApplicationArguments args = mock(ApplicationArguments.class);
        topicRepository.save(buildTopicMetadata()).get();
        // when
        when(args.getOptionValues("import.file")).thenReturn(List.of(testFile.getPath()));
        when(testCluster.getRepositories()).thenReturn(List.of(topicRepository));

        // then
        try {
            job.run(args);
            fail("job.run() should have thrown an error since there is no clearRepos option given");
        }
        catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }

    }

    @Test
    @DisplayName("should return correct job name")
    void importBackupTest_correctJobName() {
        assertEquals("import-backup", job.getJobName());

    }

    private TopicMetadata buildTopicMetadata() {
        TopicMetadata metadata = new TopicMetadata();
        metadata.setName("testtopic");
        metadata.setDescription("Testtopic description");
        metadata.setOwnerApplicationId("123");
        metadata.setType(TopicType.EVENTS);
        return metadata;
    }
}
