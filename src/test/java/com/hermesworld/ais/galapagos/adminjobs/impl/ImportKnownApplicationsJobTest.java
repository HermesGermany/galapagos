package com.hermesworld.ais.galapagos.adminjobs.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermesworld.ais.galapagos.applications.impl.KnownApplicationImpl;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.boot.ApplicationArguments;


public class ImportKnownApplicationsJobTest {


    private KafkaClusters kafkaClusters;

    private File fileWithOutInfoUrl = new File("src/test/resources/test-applications.json");

    private File fileWithInfoUrl = new File("src/test/resources/test-applications-infoUrl.json");

    private TopicBasedRepositoryMock<KnownApplicationImpl> appRepository;

    private ObjectMapper mapper;


    @Before
    public void setUp() {

        mapper = JsonUtil.newObjectMapper();
        kafkaClusters = mock(KafkaClusters.class);
        KafkaCluster testCluster = mock(KafkaCluster.class);
        when(testCluster.getId()).thenReturn("test");
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
        appRepository = new TopicBasedRepositoryMock<>();
        when(kafkaClusters.getGlobalRepository("known-applications", KnownApplicationImpl.class)).thenReturn(appRepository);

    }

    @Test
    public void reImportAfterAppChanges() throws Exception {

        List<KnownApplicationImpl> knownApplications =
            mapper.readValue(fileWithOutInfoUrl, new TypeReference<List<KnownApplicationImpl>>() {
            });

        ImportKnownApplicationsJob job = new ImportKnownApplicationsJob(kafkaClusters);
        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("applications.import.file")).thenReturn(Collections.singletonList(fileWithInfoUrl.getPath()));
        knownApplications.forEach(app -> appRepository.save(app));

        // redirect STDOUT to check update count
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;

        try {
            System.setOut(new PrintStream(buffer));
            job.run(args);
        } finally {
            System.setOut(oldOut);
        }

        String output = new String(buffer.toByteArray(), StandardCharsets.UTF_8);

        assertTrue(output.contains("\n1 new application(s) imported."));
        assertEquals("https://www.google.com", appRepository.getObject("app-1").get().getInfoUrl().toString());

    }

    @Test
    public void importApps_alreadyIdentical() throws Exception {

        List<KnownApplicationImpl> knownApplications =
            mapper.readValue(fileWithOutInfoUrl, new TypeReference<List<KnownApplicationImpl>>() {
            });

        ImportKnownApplicationsJob job = new ImportKnownApplicationsJob(kafkaClusters);
        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("applications.import.file")).thenReturn(Collections.singletonList(fileWithOutInfoUrl.getPath()));
        TopicBasedRepositoryMock<KnownApplicationImpl> appRepository = new TopicBasedRepositoryMock<>();
        knownApplications.forEach(app -> appRepository.save(app));
        when(kafkaClusters.getGlobalRepository("known-applications", KnownApplicationImpl.class)).thenReturn(appRepository);

        // redirect STDOUT to check update count
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;

        try {
            System.setOut(new PrintStream(buffer));
            job.run(args);
        } finally {
            System.setOut(oldOut);
        }

        String output = new String(buffer.toByteArray(), StandardCharsets.UTF_8);

        assertTrue(output.contains("\n0 new application(s) imported."));

    }

    @Test
    public void importApps_positiv() throws Exception {

        ImportKnownApplicationsJob job = new ImportKnownApplicationsJob(kafkaClusters);
        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("applications.import.file")).thenReturn(Collections.singletonList(fileWithOutInfoUrl.getPath()));
        TopicBasedRepositoryMock<KnownApplicationImpl> appRepository = new TopicBasedRepositoryMock<>();
        when(kafkaClusters.getGlobalRepository("known-applications", KnownApplicationImpl.class)).thenReturn(appRepository);

        // redirect STDOUT to check update count
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;

        try {
            System.setOut(new PrintStream(buffer));
            job.run(args);
        } finally {
            System.setOut(oldOut);
        }

        String output = new String(buffer.toByteArray(), StandardCharsets.UTF_8);

        assertTrue(output.contains("\n5 new application(s) imported."));
        assertEquals(appRepository.getObjects().size(), 5);
        assertTrue(appRepository.getObject("2222").isPresent());
        assertEquals(appRepository.getObject("F.I.V.E").get().getName(), "High Five");

    }

}