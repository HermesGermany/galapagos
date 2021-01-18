package com.hermesworld.ais.galapagos.applications.impl;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.applications.config.ApplicationsConfig;
import com.hermesworld.ais.galapagos.certificates.CaManager;
import com.hermesworld.ais.galapagos.certificates.CertificateSignResult;
import com.hermesworld.ais.galapagos.events.GalapagosEventManager;
import com.hermesworld.ais.galapagos.events.GalapagosEventManagerMock;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.util.TimeService;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

public class ApplicationsServiceImplTest {

    private KafkaClusters kafkaClusters;

    private KafkaCluster testCluster;

    private final TopicBasedRepository<ApplicationOwnerRequest> requestRepository = new TopicBasedRepositoryMock<>();

    private final TopicBasedRepository<KnownApplicationImpl> knownApplicationRepository = new TopicBasedRepositoryMock<>();

    private final TopicBasedRepository<ApplicationMetadata> applicationMetadataRepository = new TopicBasedRepositoryMock<>();

    @Before
    public void feedMocks() {
        kafkaClusters = mock(KafkaClusters.class);

        when(kafkaClusters.getGlobalRepository("application-owner-requests", ApplicationOwnerRequest.class))
                .thenReturn(requestRepository);
        when(kafkaClusters.getGlobalRepository("known-applications", KnownApplicationImpl.class))
                .thenReturn(knownApplicationRepository);

        testCluster = mock(KafkaCluster.class);
        when(testCluster.getRepository("application-metadata", ApplicationMetadata.class))
                .thenReturn(applicationMetadataRepository);

        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
    }

    @Test
    public void testRemoveOldRequests() {
        List<ApplicationOwnerRequest> daos = new ArrayList<>();

        ZonedDateTime createdAt = ZonedDateTime.of(LocalDateTime.of(2019, 1, 1, 10, 0), ZoneId.systemDefault());
        ZonedDateTime statusChange1 = ZonedDateTime.of(LocalDateTime.of(2019, 3, 1, 10, 0), ZoneId.systemDefault());
        ZonedDateTime statusChange2 = ZonedDateTime.of(LocalDateTime.of(2019, 3, 1, 15, 0), ZoneId.systemDefault());
        ZonedDateTime statusChange3 = ZonedDateTime.of(LocalDateTime.of(2019, 2, 28, 15, 0), ZoneId.systemDefault());
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2019, 3, 31, 14, 0), ZoneId.systemDefault());

        ApplicationOwnerRequest dao = createRequestDao("req1", createdAt, "testuser");
        dao.setLastStatusChangeAt(statusChange1);
        dao.setState(RequestState.APPROVED);
        daos.add(dao);
        dao = createRequestDao("req2", createdAt, "testuser");
        dao.setLastStatusChangeAt(statusChange1);
        dao.setState(RequestState.REJECTED);
        daos.add(dao);
        dao = createRequestDao("req3", createdAt, "testuser");
        dao.setLastStatusChangeAt(statusChange2);
        dao.setState(RequestState.REJECTED);
        daos.add(dao);
        dao = createRequestDao("req4", createdAt, "testuser");
        dao.setLastStatusChangeAt(statusChange3);
        dao.setState(RequestState.REVOKED);
        daos.add(dao);
        dao = createRequestDao("req5", createdAt, "testuser");
        dao.setLastStatusChangeAt(statusChange3);
        dao.setState(RequestState.SUBMITTED);
        daos.add(dao);

        daos.forEach(requestRepository::save);

        ApplicationsServiceImpl service = new ApplicationsServiceImpl(kafkaClusters, mock(CurrentUserService.class),
                () -> now, new GalapagosEventManagerMock(), mock(ApplicationsConfig.class));

        service.removeOldRequests();

        assertTrue(requestRepository.getObject("req2").isEmpty());
        assertTrue(requestRepository.getObject("req4").isEmpty());
        assertFalse(requestRepository.getObject("req1").isEmpty());
        assertFalse(requestRepository.getObject("req3").isEmpty());
        assertFalse(requestRepository.getObject("req5").isEmpty());
    }

    @Test
    public void testKnownApplicationAfterSubmitting() {
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
        String testUserName = "test";
        String appId = "42";
        ApplicationOwnerRequest appOwnReq = createRequestWithApplicationID(appId, "1", RequestState.SUBMITTED, now,
                testUserName);

        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.getCurrentUserName()).thenReturn(Optional.of(appOwnReq.getUserName()));

        KnownApplicationImpl knownApp = new KnownApplicationImpl(appOwnReq.getApplicationId(), "App1");
        KnownApplicationImpl knownApp2 = new KnownApplicationImpl("43", "App2");

        knownApplicationRepository.save(knownApp);
        knownApplicationRepository.save(knownApp2);

        requestRepository.save(appOwnReq);

        ApplicationsServiceImpl applicationServiceImpl = new ApplicationsServiceImpl(kafkaClusters, currentUserService,
                mock(TimeService.class), mock(GalapagosEventManager.class), mock(ApplicationsConfig.class));
        List<? extends KnownApplication> result = applicationServiceImpl.getKnownApplications(true);

        for (KnownApplication resultKnownApp : result) {
            assertNotEquals(appId, resultKnownApp.getId());
        }
        assertEquals(1, result.size());
        assertEquals("43", result.get(0).getId());
    }

    /**
     * Tests that, when I re-register an application on a given environment, but change the prefix for internal topics,
     * this change is stored in the metadata.
     */
    @Test
    public void testRegisterApplication_changedTopicPrefix() throws Exception {
        KnownApplicationImpl appl = new KnownApplicationImpl("app-1", "TestApp");
        appl.setAliases(List.of("a1", "app1", "ta1"));

        knownApplicationRepository.save(appl);

        CaManager caMan = mock(CaManager.class);

        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getNotAfter()).thenReturn(new Date());

        CertificateSignResult signResult = new CertificateSignResult(cert, "abc", "cn=testapp", new byte[0]);
        when(caMan.createApplicationCertificateAndPrivateKey(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(signResult));
        when(kafkaClusters.getCaManager("test")).thenReturn(Optional.of(caMan));

        ApplicationsConfig config = new ApplicationsConfig();
        config.setConsumerGroupPrefix("test.group.");
        config.setTopicPrefixFormat("{0}.internal.");

        ApplicationsServiceImpl service = new ApplicationsServiceImpl(kafkaClusters, mock(CurrentUserService.class),
                mock(TimeService.class), new GalapagosEventManagerMock(), config);

        // first, create the application the usual way
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ApplicationMetadata metadata = service
                .createApplicationCertificateAndPrivateKey("test", "app-1", "testapp.internal.", baos).get();

        assertEquals("app-1", metadata.getApplicationId());
        assertEquals("testapp.internal.", metadata.getTopicPrefix());

        // re-create application now with different topic prefix
        metadata = service.createApplicationCertificateAndPrivateKey("test", "app-1", "a1.internal.", baos).get();
        assertEquals("app-1", metadata.getApplicationId());
        assertEquals("a1.internal.", metadata.getTopicPrefix());

        // re-get application from repo to be sure
        metadata = service.getApplicationMetadata("test", "app-1").get();
        assertEquals("app-1", metadata.getApplicationId());
        assertEquals("a1.internal.", metadata.getTopicPrefix());
    }

    @Test
    public void testExtendCertificate() throws Exception {
        ApplicationsConfig config = new ApplicationsConfig();
        config.setConsumerGroupPrefix("test.group.");
        config.setTopicPrefixFormat("{0}.internal.");

        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getNotAfter()).thenReturn(new Date());

        CaManager caMan = mock(CaManager.class);
        when(kafkaClusters.getCaManager("test")).thenReturn(Optional.of(caMan));
        when(caMan.extendApplicationCertificate(any(), any())).then(inv -> CompletableFuture
                .completedFuture(new CertificateSignResult(cert, "", inv.getArgument(0), new byte[0])));

        ApplicationsServiceImpl service = new ApplicationsServiceImpl(kafkaClusters, mock(CurrentUserService.class),
                mock(TimeService.class), new GalapagosEventManagerMock(), config);

        String testCsrData = StreamUtils.copyToString(
                new ClassPathResource("/certificates/test_quattroExtend.csr").getInputStream(), StandardCharsets.UTF_8);

        KnownApplicationImpl app = new KnownApplicationImpl("quattro-1", "Quattro");
        knownApplicationRepository.save(app).get();

        ApplicationMetadata appl = new ApplicationMetadata();
        appl.setApplicationId("quattro-1");
        appl.setDn("CN=quattro,OU=certification_12345");
        appl.setTopicPrefix("quattro.internal.");
        applicationMetadataRepository.save(appl).get();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ApplicationMetadata meta = service
                .createApplicationCertificateFromCsr("test", "quattro-1", testCsrData, "quattro.internal.", true, baos)
                .get();
        assertEquals("CN=quattro,OU=certification_12345", meta.getDn());
        assertEquals("CN=quattro,OU=certification_12345",
                applicationMetadataRepository.getObject("quattro-1").map(o -> o.getDn()).orElseThrow());
    }

    private static ApplicationOwnerRequest createRequestDao(String id, ZonedDateTime createdAt, String userName) {
        ApplicationOwnerRequest dao = new ApplicationOwnerRequest();
        dao.setCreatedAt(createdAt);
        dao.setId(id);
        dao.setUserName(userName);
        dao.setState(RequestState.SUBMITTED);
        return dao;
    }

    private static ApplicationOwnerRequest createRequestWithApplicationID(String appID, String id,
            RequestState reqState, ZonedDateTime createdAt, String userName) {
        ApplicationOwnerRequest dao = new ApplicationOwnerRequest();
        dao.setApplicationId(appID);
        dao.setCreatedAt(createdAt);
        dao.setId(id);
        dao.setUserName(userName);
        dao.setState(reqState);
        return dao;

    }

}
