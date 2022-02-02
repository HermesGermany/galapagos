package com.hermesworld.ais.galapagos.devcerts.impl;

import com.hermesworld.ais.galapagos.devcerts.DevCertificateMetadata;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.TimeService;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DeveloperCertificateServiceImplTest {

//    private List<InvocationOnMock> updateAclCalls;
    private List<InvocationOnMock> removeAclCalls;

//    private long expiryDate;

    private TopicBasedRepositoryMock<DevCertificateMetadata> testRepo;

    private TopicBasedRepositoryMock<DevCertificateMetadata> prodRepo;

    private DeveloperCertificateServiceImpl service;

    private KafkaCluster testCluster;

    private KafkaCluster prodCluster;

    private TimeService timeService;

    private DevUserAclListener aclUpdater;

    @Captor
    ArgumentCaptor<Set<DevCertificateMetadata>> argumentCaptor;

    @Before
    public void initMocks() {
        MockitoAnnotations.openMocks(this);
        KafkaClusters clusters = mock(KafkaClusters.class);
        CurrentUserService userService = mock(CurrentUserService.class);
        aclUpdater = mock(DevUserAclListener.class);

        when(userService.getCurrentUserName()).thenReturn(Optional.of("testuser"));

//        updateAclCalls = new ArrayList<>();
        removeAclCalls = new ArrayList<>();

//        when(aclUpdater.updateAcls(any(), any())).then(inv -> {
//            updateAclCalls.add(inv);
//            return FutureUtil.noop();
//        });
//
        when(aclUpdater.removeAcls(any(), any())).then(inv -> {
            removeAclCalls.add(inv);
            return FutureUtil.noop();
        });

//        byte[] testData = new byte[1024];
//        Arrays.fill(testData, (byte) 23);
//
//        CaManager caManager = mock(CaManager.class);
//        X509Certificate cert = mock(X509Certificate.class);
//
//        expiryDate = LocalDateTime.of(2021, 10, 1, 10, 0, 0).toEpochSecond(ZoneOffset.UTC) * 1000l;
//
//        when(cert.getNotAfter()).thenReturn(new Date(expiryDate));
//        CertificateSignResult result = new CertificateSignResult(cert, "abc", "CN=testuser", testData);
//        when(caManager.createDeveloperCertificateAndPrivateKey("testuser"))
//                .thenReturn(CompletableFuture.completedFuture(result));
//        when(clusters.getCaManager("_test")).thenReturn(Optional.of(caManager));
//
        KafkaEnvironmentConfig config = mock(KafkaEnvironmentConfig.class);
        when(config.getAuthenticationMode()).thenReturn("certificates");
        when(clusters.getEnvironmentMetadata(any())).thenReturn(Optional.of(config));
        when(clusters.getEnvironmentMetadata(any())).thenReturn(Optional.of(config));
        testCluster = mock(KafkaCluster.class);
        when(clusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
        prodCluster = mock(KafkaCluster.class);
        when(clusters.getEnvironment("prod")).thenReturn(Optional.of(prodCluster));
        when(clusters.getEnvironments()).thenReturn(List.of(testCluster, prodCluster));
        testRepo = new TopicBasedRepositoryMock<>();
        prodRepo = new TopicBasedRepositoryMock<>();
        when(testCluster.getRepository("devcerts", DevCertificateMetadata.class)).thenReturn(testRepo);
        when(prodCluster.getRepository("devcerts", DevCertificateMetadata.class)).thenReturn(prodRepo);
        timeService = mock(TimeService.class);
        when(timeService.getTimestamp()).thenReturn(ZonedDateTime.now());
        service = new DeveloperCertificateServiceImpl(clusters, userService, aclUpdater, timeService);

//    }
//
//    @Test
//    public void testCreateDeveloperCertificate() throws Exception {
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//
//        service.createDeveloperCertificateForCurrentUser("_test", baos).get();
//
//        byte[] certificate = baos.toByteArray();
//        assertTrue(certificate.length == 1024);
//
//        assertEquals(1, updateAclCalls.size());
//
//        assertEquals(1, repository.getObjects().size());
//        assertEquals("testuser", repository.getObject("testuser").get().getUserName());
//        assertEquals(expiryDate, repository.getObject("testuser").get().getExpiryDate().toEpochMilli());
//        assertEquals("CN=testuser", repository.getObject("testuser").get().getCertificateDn());
//    }
//
//    @Test
//    public void testRemoveOldAcls() throws Exception {
//        DevCertificateMetadata oldMeta = new DevCertificateMetadata();
//        oldMeta.setUserName("testuser");
//        oldMeta.setCertificateDn("CN=testuser,OU=old");
//        oldMeta.setExpiryDate(Instant.now());
//        repository.save(oldMeta).get();
//
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//
//        service.createDeveloperCertificateForCurrentUser("_test", baos).get();
//
//        assertEquals(1, updateAclCalls.size());
//        assertEquals(1, removeAclCalls.size());
//
//        Set<DevCertificateMetadata> oldSet = removeAclCalls.get(0).getArgument(1);
//        Set<DevCertificateMetadata> newSet = updateAclCalls.get(0).getArgument(1);
//        assertEquals("CN=testuser,OU=old", oldSet.iterator().next().getCertificateDn());
//        assertEquals("CN=testuser", newSet.iterator().next().getCertificateDn());
//
//        assertEquals(1, repository.getObjects().size());
//        assertEquals("testuser", repository.getObject("testuser").get().getUserName());
//        assertEquals(expiryDate, repository.getObject("testuser").get().getExpiryDate().toEpochMilli());
//        assertEquals("CN=testuser", repository.getObject("testuser").get().getCertificateDn());
    }

    @Test
    @DisplayName("should call removeAcls() method for clearing ACLs of expired developer certificates")
    public void aclsShouldBeRemoved_positive() throws ExecutionException, InterruptedException {
        DevCertificateMetadata devCert = new DevCertificateMetadata();
        devCert.setUserName("testUser");
        devCert.setExpiryDate(Instant.now().minus(Duration.ofDays(1000)));
        testRepo.save(devCert).get();

        DevCertificateMetadata devCert2 = new DevCertificateMetadata();
        devCert2.setUserName("testUser2");
        devCert2.setExpiryDate(Instant.now().minus(Duration.ofDays(1000)));
        prodRepo.save(devCert2).get();

        Integer clearedCerts = service.clearExpiredDeveloperCertificatesOnAllClusters().get();
        assertEquals(2, clearedCerts.intValue());

        verify(aclUpdater, times(2)).removeAcls(any(), argumentCaptor.capture());

        List<Set<DevCertificateMetadata>> list = argumentCaptor.getAllValues();

        assertEquals("testUser", list.get(0).stream().findFirst().get().getUserName());
        assertEquals("testUser2", list.get(1).stream().findFirst().get().getUserName());

    }

    @Test
    @DisplayName("should not call removeAcls() for valid developer certificates")
    public void dontCallRemoveAclForValidCertificate() throws ExecutionException, InterruptedException {
        fillRepos(1, 3);
        DevCertificateMetadata devCert = new DevCertificateMetadata();
        devCert.setUserName("testUser");
        devCert.setExpiryDate(Instant.now().plus(Duration.ofDays(1000)));
        testRepo.save(devCert).get();
        service.clearExpiredDeveloperCertificatesOnAllClusters().get();

        assertEquals(2, removeAclCalls.size());
        assertTrue(testRepo.getObject("testUser").isPresent());
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
