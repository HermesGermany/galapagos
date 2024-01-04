package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermesworld.ais.galapagos.applications.impl.KnownApplicationImpl;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportKnownApplicationsJobTest {

    private KafkaClusters kafkaClusters;

    private final File fileWithOutInfoUrl = new File("src/test/resources/test-applications.json");

    private final File fileWithInfoUrl = new File("src/test/resources/test-applications-infoUrl.json");

    private TopicBasedRepositoryMock<KnownApplicationImpl> appRepository;

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {

        mapper = JsonUtil.newObjectMapper();
        kafkaClusters = mock(KafkaClusters.class);
        KafkaCluster testCluster = mock(KafkaCluster.class);
        when(testCluster.getId()).thenReturn("test");
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
        appRepository = new TopicBasedRepositoryMock<>();
        when(kafkaClusters.getGlobalRepository("known-applications", KnownApplicationImpl.class))
                .thenReturn(appRepository);

    }

    @Test
    void reImportAfterAppChanges() throws Exception {
        List<KnownApplicationImpl> knownApplications = mapper.readValue(fileWithOutInfoUrl, new TypeReference<>() {
        });

        ImportKnownApplicationsJob job = new ImportKnownApplicationsJob(kafkaClusters);
        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("applications.import.file")).thenReturn(List.of(fileWithInfoUrl.getPath()));
        knownApplications.forEach(app -> safeGet(appRepository.save(app)));

        // redirect STDOUT to check update count
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;

        try {
            System.setOut(new PrintStream(buffer));
            job.run(args);
        }
        finally {
            System.setOut(oldOut);
        }

        String output = buffer.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("\n1 new application(s) imported."));
        // noinspection OptionalGetWithoutIsPresent
        assertEquals("https://www.google.com", appRepository.getObject("app-1").get().getInfoUrl().toString());
    }

    @Test
    void importApps_alreadyIdentical() throws Exception {

        List<KnownApplicationImpl> knownApplications = mapper.readValue(fileWithOutInfoUrl, new TypeReference<>() {
        });

        ImportKnownApplicationsJob job = new ImportKnownApplicationsJob(kafkaClusters);
        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("applications.import.file")).thenReturn(List.of(fileWithOutInfoUrl.getPath()));
        TopicBasedRepositoryMock<KnownApplicationImpl> appRepository = new TopicBasedRepositoryMock<>();
        knownApplications.forEach(app -> safeGet(appRepository.save(app)));
        when(kafkaClusters.getGlobalRepository("known-applications", KnownApplicationImpl.class))
                .thenReturn(appRepository);

        // redirect STDOUT to check update count
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;

        try {
            System.setOut(new PrintStream(buffer));
            job.run(args);
        }
        finally {
            System.setOut(oldOut);
        }

        String output = buffer.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("\n0 new application(s) imported."));

    }

    @Test
    void importApps_positiv() throws Exception {

        ImportKnownApplicationsJob job = new ImportKnownApplicationsJob(kafkaClusters);
        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("applications.import.file")).thenReturn(List.of(fileWithOutInfoUrl.getPath()));
        TopicBasedRepositoryMock<KnownApplicationImpl> appRepository = new TopicBasedRepositoryMock<>();
        when(kafkaClusters.getGlobalRepository("known-applications", KnownApplicationImpl.class))
                .thenReturn(appRepository);

        // redirect STDOUT to check update count
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;

        try {
            System.setOut(new PrintStream(buffer));
            job.run(args);
        }
        finally {
            System.setOut(oldOut);
        }

        String output = buffer.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("\n5 new application(s) imported."));
        assertEquals(5, appRepository.getObjects().size());
        assertTrue(appRepository.getObject("2222").isPresent());
        // noinspection OptionalGetWithoutIsPresent
        assertEquals("High Five", appRepository.getObject("F.I.V.E").get().getName());

    }

    private void safeGet(CompletableFuture<?> future) {
        try {
            future.get();
        }
        catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}