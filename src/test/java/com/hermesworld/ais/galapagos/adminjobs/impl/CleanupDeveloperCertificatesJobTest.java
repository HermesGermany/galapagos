package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.devcerts.DevCertificateMetadata;
import com.hermesworld.ais.galapagos.devcerts.impl.DevUserAclListener;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.TimeService;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.ApplicationArguments;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class CleanupDeveloperCertificatesJobTest {

    private KafkaClusters kafkaClusters;

    private TimeService timeService;

    private DevUserAclListener aclListener;

    private final TopicBasedRepositoryMock<DevCertificateMetadata> testRepo = new TopicBasedRepositoryMock<>();

    private final TopicBasedRepositoryMock<DevCertificateMetadata> prodRepo = new TopicBasedRepositoryMock<>();

    @Before
    public void setUp() {
        kafkaClusters = mock(KafkaClusters.class);
        KafkaCluster testCluster = mock(KafkaCluster.class);
        KafkaCluster prodCluster = mock(KafkaCluster.class);
        when(testCluster.getId()).thenReturn("test");
        when(prodCluster.getId()).thenReturn("prod");
        KafkaEnvironmentConfig config = mock(KafkaEnvironmentConfig.class);
        when(config.getAuthenticationMode()).thenReturn("certificates");
        when(kafkaClusters.getEnvironmentMetadata("test")).thenReturn(Optional.of(config));
        when(kafkaClusters.getEnvironmentMetadata("prod")).thenReturn(Optional.of(config));
        when(testCluster.getRepository("devcerts", DevCertificateMetadata.class)).thenReturn(testRepo);
        when(prodCluster.getRepository("devcerts", DevCertificateMetadata.class)).thenReturn(prodRepo);
        when(kafkaClusters.getEnvironments()).thenReturn(List.of(testCluster, prodCluster));
        timeService = mock(TimeService.class);
        when(timeService.getTimestamp()).thenReturn(ZonedDateTime.now());
        aclListener = mock(DevUserAclListener.class);
        when(aclListener.removeAcls(any(), any())).thenReturn(FutureUtil.noop());
    }

    @Test
    @DisplayName("should clear right number of developer certificates")
    public void clearExpiredDevCerts_positive() {
        CleanupDeveloperCertificatesJob job = new CleanupDeveloperCertificatesJob(kafkaClusters, aclListener,
                timeService);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;

        try {
            System.setOut(new PrintStream(baos));
            fillRepos(2, 3);
            job.run(mock(ApplicationArguments.class));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            System.setOut(oldOut);
        }

        String output = baos.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("5"));

    }

    @Test
    @DisplayName("should call removeAcls() methode for clearing ACLs of expired developer certificates")
    public void aclsShouldBeRemoved_positive() {
        CleanupDeveloperCertificatesJob job = new CleanupDeveloperCertificatesJob(kafkaClusters, aclListener,
                timeService);

        try {
            fillRepos(2, 3);
            job.run(mock(ApplicationArguments.class));
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        verify(aclListener, times(5)).removeAcls(any(), any());

    }

    private void fillRepos(int howManyCertsTest, int howManyCertsProd) throws ExecutionException, InterruptedException {
        for (int i = 1; i <= howManyCertsTest; i++) {
            // 4 random numbers in range 0 to 9
            String randomUser = new Random().ints(4, 0, 9).mapToObj(String::valueOf).collect(Collectors.joining());
            DevCertificateMetadata devCert = new DevCertificateMetadata();
            devCert.setUserName("RandomUser:" + randomUser);
            devCert.setExpiryDate(Instant.now().minus(Duration.ofDays(1000)));
            testRepo.save(devCert).get();
        }

        for (int i = 1; i <= howManyCertsProd; i++) {
            // 4 random numbers in range 0 to 9
            String randomUser = new Random().ints(4, 0, 9).mapToObj(String::valueOf).collect(Collectors.joining());
            DevCertificateMetadata devCert = new DevCertificateMetadata();
            devCert.setUserName("RandomUser:" + randomUser);
            devCert.setExpiryDate(Instant.now().minus(Duration.ofDays(1000)));
            prodRepo.save(devCert).get();
        }

    }
}
