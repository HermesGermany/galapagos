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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ImportBackupJobTest {

    private ImportBackupJob job;

    private KafkaClusters kafkaClusters;

    private KafkaCluster testCluster;

    private final File testFile = new File("src/test/resources/backup-test.json");

    private TopicBasedRepositoryMock<TopicMetadata> topicRepository;

    @BeforeEach
    public void setUp() {
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
    public void importBackupTest() throws Exception {
        // given
        ApplicationArguments args = mock(ApplicationArguments.class);
        // when
        when(args.getOptionValues("import.file")).thenReturn(Collections.singletonList(testFile.getPath()));
        when(args.getOptionValues("clearRepos")).thenReturn(Collections.singletonList("false"));
        when(testCluster.getRepositories()).thenReturn(Collections.singletonList(topicRepository));
        // then
        job.run(args);

        assertTrue(topicRepository.getObject("de.hlg.events.sales.my-topic-nice").isPresent());

    }

    @Test
    @DisplayName("should import backup from import file and old metadata in repos should still be present")
    public void importBackupWithoutClearingExistingRepos() throws Exception {
        // given
        ApplicationArguments args = mock(ApplicationArguments.class);
        topicRepository.save(buildTopicMetadata()).get();
        // when
        when(args.getOptionValues("import.file")).thenReturn(Collections.singletonList(testFile.getPath()));
        when(args.getOptionValues("clearRepos")).thenReturn(Collections.singletonList("false"));
        when(testCluster.getRepositories()).thenReturn(Collections.singletonList(topicRepository));

        // then
        job.run(args);
        assertTrue(topicRepository.getObject("de.hlg.events.sales.my-topic-nice").isPresent());
        assertTrue(topicRepository.getObjects().size() == 2);

    }

    @Test
    @DisplayName("should import backup from import file and old metadata in repos should be not present")
    public void importBackupWithClearingExistingRepos() throws Exception {
        // given
        ApplicationArguments args = mock(ApplicationArguments.class);
        topicRepository.save(buildTopicMetadata()).get();
        // when
        when(args.getOptionValues("import.file")).thenReturn(Collections.singletonList(testFile.getPath()));
        when(args.getOptionValues("clearRepos")).thenReturn(Collections.singletonList("true"));
        when(testCluster.getRepositories()).thenReturn(Collections.singletonList(topicRepository));

        // then
        job.run(args);
        assertTrue(topicRepository.getObject("de.hlg.events.sales.my-topic-nice").isPresent());
        assertTrue(topicRepository.getObjects().size() == 1);

    }

    @Test
    @DisplayName("should throw exception because no import file is set")
    public void importBackupTest_noFileOption() throws Exception {
        // given
        ApplicationArguments args = mock(ApplicationArguments.class);
        topicRepository.save(buildTopicMetadata()).get();
        // when
        when(args.getOptionValues("clearRepos")).thenReturn(Collections.singletonList("true"));
        when(testCluster.getRepositories()).thenReturn(Collections.singletonList(topicRepository));

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
    public void importBackupTest_noClearReposOption() throws Exception {
        // given
        ApplicationArguments args = mock(ApplicationArguments.class);
        topicRepository.save(buildTopicMetadata()).get();
        // when
        when(args.getOptionValues("import.file")).thenReturn(Collections.singletonList(testFile.getPath()));
        when(testCluster.getRepositories()).thenReturn(Collections.singletonList(topicRepository));

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
    public void importBackupTest_correctJobName() {
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
