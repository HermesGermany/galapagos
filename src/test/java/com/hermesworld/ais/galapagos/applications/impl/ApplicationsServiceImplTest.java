package com.hermesworld.ais.galapagos.applications.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.events.GalapagosEventManagerMock;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.auth.CreateAuthenticationResult;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.naming.ApplicationPrefixes;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.naming.config.CaseStrategy;
import com.hermesworld.ais.galapagos.naming.config.NamingConfig;
import com.hermesworld.ais.galapagos.naming.impl.NamingServiceImpl;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.util.TimeService;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ApplicationsServiceImplTest {

    private KafkaClusters kafkaClusters;

    private final TopicBasedRepository<ApplicationOwnerRequest> requestRepository = new TopicBasedRepositoryMock<>();

    private final TopicBasedRepository<KnownApplicationImpl> knownApplicationRepository = new TopicBasedRepositoryMock<>();

    private final TopicBasedRepository<ApplicationMetadata> applicationMetadataRepository = new TopicBasedRepositoryMock<>();

    private final TopicBasedRepository<ApplicationMetadata> applicationMetadataRepository2 = new TopicBasedRepositoryMock<>();

    private KafkaAuthenticationModule authenticationModule;

    @BeforeEach
    void feedMocks() {
        kafkaClusters = mock(KafkaClusters.class);

        when(kafkaClusters.getGlobalRepository("application-owner-requests", ApplicationOwnerRequest.class))
                .thenReturn(requestRepository);
        when(kafkaClusters.getGlobalRepository("known-applications", KnownApplicationImpl.class))
                .thenReturn(knownApplicationRepository);

        KafkaCluster testCluster = mock(KafkaCluster.class);
        when(testCluster.getRepository("application-metadata", ApplicationMetadata.class))
                .thenReturn(applicationMetadataRepository);
        KafkaCluster testCluster2 = mock(KafkaCluster.class);
        when(testCluster2.getRepository("application-metadata", ApplicationMetadata.class))
                .thenReturn(applicationMetadataRepository2);

        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
        when(kafkaClusters.getEnvironment("test2")).thenReturn(Optional.of(testCluster2));
        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("test", "test2"));
        authenticationModule = mock(KafkaAuthenticationModule.class);

        CreateAuthenticationResult authResult = new CreateAuthenticationResult(
                new JSONObject(Map.of("testentry", true)), new byte[] { 1 });

        when(authenticationModule.createApplicationAuthentication(eq("quattro-1"), eq("quattro"), any()))
                .thenReturn(CompletableFuture.completedFuture(authResult));
        when(kafkaClusters.getAuthenticationModule("test")).thenReturn(Optional.of(authenticationModule));
        when(kafkaClusters.getAuthenticationModule("test2")).thenReturn(Optional.of(authenticationModule));
    }

    @Test
    void testRemoveOldRequests() {
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
        dao = createRequestDao("req6", createdAt, "testuser");
        dao.setLastStatusChangeAt(statusChange1);
        dao.setState(RequestState.RESIGNED);
        daos.add(dao);
        dao = createRequestDao("req7", createdAt, "testuser");
        dao.setLastStatusChangeAt(statusChange2);
        dao.setState(RequestState.RESIGNED);
        daos.add(dao);

        daos.forEach(requestRepository::save);

        ApplicationsServiceImpl service = new ApplicationsServiceImpl(kafkaClusters, mock(CurrentUserService.class),
                () -> now, mock(NamingService.class), new GalapagosEventManagerMock());

        service.removeOldRequests();

        assertTrue(requestRepository.getObject("req2").isEmpty());
        assertTrue(requestRepository.getObject("req4").isEmpty());
        assertTrue(requestRepository.getObject("req6").isEmpty());
        assertFalse(requestRepository.getObject("req1").isEmpty());
        assertFalse(requestRepository.getObject("req3").isEmpty());
        assertFalse(requestRepository.getObject("req5").isEmpty());
        assertFalse(requestRepository.getObject("req7").isEmpty());
    }

    @Test
    void testKnownApplicationAfterSubmitting() {
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
                mock(TimeService.class), mock(NamingService.class), new GalapagosEventManagerMock());
        List<? extends KnownApplication> result = applicationServiceImpl.getKnownApplications(true);

        for (KnownApplication resultKnownApp : result) {
            assertNotEquals(appId, resultKnownApp.getId());
        }
        assertEquals(1, result.size());
        assertEquals("43", result.get(0).getId());
    }

    @Test
    void testReplaceCertificate_appChanges() throws Exception {
        // Application changed e.g. in external Architecture system
        KnownApplicationImpl app = new KnownApplicationImpl("quattro-1", "Quattro");
        app.setAliases(List.of("q2"));
        knownApplicationRepository.save(app).get();

        // But is already registered with Alias Q1 and associated rights
        ApplicationMetadata appl = new ApplicationMetadata();
        appl.setApplicationId("quattro-1");
        appl.setInternalTopicPrefixes(List.of("quattro.internal.", "q1.internal."));
        appl.setConsumerGroupPrefixes(List.of("groups.quattro.", "groups.q1."));
        appl.setTransactionIdPrefixes(List.of("quattro.internal.", "q1.internal."));
        applicationMetadataRepository.save(appl).get();

        // The NamingService would return new rights
        ApplicationPrefixes newPrefixes = mock(ApplicationPrefixes.class);
        when(newPrefixes.combineWith(any())).thenCallRealMethod();
        when(newPrefixes.getInternalTopicPrefixes()).thenReturn(List.of("quattro.internal.", "q2.internal."));
        when(newPrefixes.getConsumerGroupPrefixes()).thenReturn(List.of("groups.quattro.", "groups.q2."));
        when(newPrefixes.getTransactionIdPrefixes()).thenReturn(List.of("quattro.internal.", "q2.internal."));

        NamingService namingService = mock(NamingService.class);
        when(namingService.getAllowedPrefixes(any())).thenReturn(newPrefixes);
        when(namingService.normalize("Quattro")).thenReturn("quattro");

        GalapagosEventManagerMock eventManagerMock = new GalapagosEventManagerMock();

        ApplicationsServiceImpl applicationServiceImpl = new ApplicationsServiceImpl(kafkaClusters,
                mock(CurrentUserService.class), mock(TimeService.class), namingService, eventManagerMock);

        applicationServiceImpl
                .registerApplicationOnEnvironment("test", "quattro-1", new JSONObject(), new ByteArrayOutputStream())
                .get();

        // noinspection OptionalGetWithoutIsPresent
        appl = applicationMetadataRepository.getObject("quattro-1").get();

        assertTrue(new JSONObject(appl.getAuthenticationJson()).getBoolean("testentry"));

        // resulting rights must contain BOTH old and new prefixes
        assertTrue(appl.getInternalTopicPrefixes().contains("quattro.internal."));
        assertTrue(appl.getInternalTopicPrefixes().contains("q1.internal."));
        assertTrue(appl.getInternalTopicPrefixes().contains("q2.internal."));
        assertTrue(appl.getTransactionIdPrefixes().contains("quattro.internal."));
        assertTrue(appl.getTransactionIdPrefixes().contains("q1.internal."));
        assertTrue(appl.getTransactionIdPrefixes().contains("q2.internal."));
        assertTrue(appl.getConsumerGroupPrefixes().contains("groups.quattro."));
        assertTrue(appl.getConsumerGroupPrefixes().contains("groups.q1."));
        assertTrue(appl.getConsumerGroupPrefixes().contains("groups.q2."));

        // also check (while we are here) that event has fired for update
        List<InvocationOnMock> invs = eventManagerMock.getSinkInvocations();
        assertEquals(1, invs.size());
        assertEquals("handleApplicationAuthenticationChanged", invs.get(0).getMethod().getName());
    }

    @Test
    void testRegisterNewFiresEvent() throws Exception {
        KnownApplicationImpl app = new KnownApplicationImpl("quattro-1", "Quattro");
        app.setAliases(List.of("q2"));
        knownApplicationRepository.save(app).get();

        GalapagosEventManagerMock eventManagerMock = new GalapagosEventManagerMock();

        NamingService namingService = buildNamingService();

        ApplicationsServiceImpl applicationServiceImpl = new ApplicationsServiceImpl(kafkaClusters,
                mock(CurrentUserService.class), mock(TimeService.class), namingService, eventManagerMock);

        applicationServiceImpl
                .registerApplicationOnEnvironment("test", "quattro-1", new JSONObject(), new ByteArrayOutputStream())
                .get();

        List<InvocationOnMock> invs = eventManagerMock.getSinkInvocations();
        assertEquals(1, invs.size());
        assertEquals("handleApplicationRegistered", invs.get(0).getMethod().getName());
    }

    @Test
    void testPrefix() throws Exception {
        KnownApplicationImpl app = new KnownApplicationImpl("quattro-1", "Quattro");
        app.setAliases(List.of("q2"));
        knownApplicationRepository.save(app).get();
        NamingService namingService = buildNamingService_forStagePrefixes();
        GalapagosEventManagerMock eventManagerMock = new GalapagosEventManagerMock();

        ApplicationMetadata appl = new ApplicationMetadata();
        appl.setApplicationId("quattro-1");
        appl.setInternalTopicPrefixes(List.of("quattro.internal.", "q2.internal."));
        applicationMetadataRepository.save(appl).get();

        ApplicationsServiceImpl applicationServiceImpl = new ApplicationsServiceImpl(kafkaClusters,
                mock(CurrentUserService.class), mock(TimeService.class), namingService, eventManagerMock);

        applicationServiceImpl
                .registerApplicationOnEnvironment("test", "quattro-1", new JSONObject(), new ByteArrayOutputStream())
                .get();

        app.setAliases(List.of("q3"));
        knownApplicationRepository.save(app).get();

        ApplicationMetadata appl2 = applicationServiceImpl
                .registerApplicationOnEnvironment("test2", "quattro-1", new JSONObject(), new ByteArrayOutputStream())
                .get();

        assertEquals(Set.of("quattro.internal.", "q2.internal.", "q3.internal."),
                new HashSet<>(appl2.getInternalTopicPrefixes()));
    }

    @Test
    void testExtendCertificate() throws Exception {
        // TODO move to CertificeAuthenticationModuleTest

//        KnownApplicationImpl app = new KnownApplicationImpl("quattro-1", "Quattro");
//        knownApplicationRepository.save(app).get();
//
//        NamingService namingService = mock(NamingService.class);
//        ApplicationPrefixes prefixes = mock(ApplicationPrefixes.class);
//        when(namingService.getAllowedPrefixes(any())).then(inv -> {
//            KnownApplication appl = inv.getArgument(0);
//            assertEquals("quattro-1", appl.getId());
//            return prefixes;
//        });
//        when(prefixes.combineWith(any())).thenCallRealMethod();
//
//        when(prefixes.getInternalTopicPrefixes()).thenReturn(List.of("quattro.internal."));
//        when(prefixes.getConsumerGroupPrefixes()).thenReturn(List.of("test.group.quattro."));
//        when(prefixes.getTransactionIdPrefixes()).thenReturn(List.of("quattro.internal."));
//
//        X509Certificate cert = mock(X509Certificate.class);
//        when(cert.getNotAfter()).thenReturn(new Date());
//
//        CaManager caMan = mock(CaManager.class);
//        when(kafkaClusters.getCaManager("test")).thenReturn(Optional.of(caMan));
//        when(caMan.extendApplicationCertificate(any(), any())).then(inv -> CompletableFuture
//                .completedFuture(new CertificateSignResult(cert, "", inv.getArgument(0), new byte[0])));
//
//        ApplicationsServiceImpl service = new ApplicationsServiceImpl(kafkaClusters, mock(CurrentUserService.class),
//                mock(TimeService.class), namingService, new GalapagosEventManagerMock());
//
//        String testCsrData = StreamUtils.copyToString(
//                new ClassPathResource("/certificates/test_quattroExtend.csr").getInputStream(), StandardCharsets.UTF_8);
//
//        ApplicationMetadata appl = new ApplicationMetadata();
//        appl.setApplicationId("quattro-1");
//        appl.setDn("CN=quattro,OU=certification_12345");
//        appl.setInternalTopicPrefixes(List.of("quattro.internal."));
//        applicationMetadataRepository.save(appl).get();
//
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        ApplicationMetadata meta = service
//                .createApplicationCertificateFromCsr("test", "quattro-1", testCsrData, true, baos).get();
//        assertEquals("CN=quattro,OU=certification_12345", meta.getDn());
//        assertEquals("CN=quattro,OU=certification_12345",
//                applicationMetadataRepository.getObject("quattro-1").map(o -> o.getDn()).orElseThrow());
    }

    @Test
    void testUpdateAuthentication() throws Exception {
        // WHEN an already registered application is re-registered on an environment...
        KnownApplicationImpl app = new KnownApplicationImpl("quattro-1", "Quattro");
        knownApplicationRepository.save(app).get();

        ApplicationMetadata appl = new ApplicationMetadata();
        appl.setApplicationId("quattro-1");
        appl.setAuthenticationJson("{}");
        appl.setInternalTopicPrefixes(List.of("quattro.internal.", "q1.internal."));
        appl.setConsumerGroupPrefixes(List.of("groups.quattro.", "groups.q1."));
        appl.setTransactionIdPrefixes(List.of("quattro.internal.", "q1.internal."));
        applicationMetadataRepository.save(appl).get();

        ApplicationsServiceImpl applicationServiceImpl = new ApplicationsServiceImpl(kafkaClusters,
                mock(CurrentUserService.class), mock(TimeService.class), buildNamingService(),
                new GalapagosEventManagerMock());

        CreateAuthenticationResult authResult = new CreateAuthenticationResult(new JSONObject(), new byte[] { 1 });
        when(authenticationModule.createApplicationAuthentication(any(), any(), any()))
                .thenThrow(new IllegalStateException(
                        "createApplicationAuthentication() should not be called for already registered application"));
        when(authenticationModule.updateApplicationAuthentication(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(authResult));

        applicationServiceImpl
                .registerApplicationOnEnvironment("test", "quattro-1", new JSONObject(), new ByteArrayOutputStream())
                .get();

        // THEN updateApplicationAuthentication instead of create... must be used by the implementation.
        verify(authenticationModule).updateApplicationAuthentication(eq("quattro-1"), any(), any(), any());
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

    private static NamingService buildNamingService() {
        NamingService namingService = mock(NamingService.class);
        ApplicationPrefixes prefixes = mock(ApplicationPrefixes.class);
        when(namingService.getAllowedPrefixes(any())).then(inv -> {
            KnownApplication appl = inv.getArgument(0);
            assertEquals("quattro-1", appl.getId());
            return prefixes;
        });
        when(prefixes.combineWith(any())).thenCallRealMethod();
        when(prefixes.getInternalTopicPrefixes()).thenReturn(List.of("quattro.internal."));
        when(prefixes.getConsumerGroupPrefixes()).thenReturn(List.of("test.group.quattro."));
        when(prefixes.getTransactionIdPrefixes()).thenReturn(List.of("quattro.internal."));

        when(namingService.normalize("Quattro")).thenReturn("quattro");
        return namingService;
    }

    private static NamingService buildNamingService_forStagePrefixes() {
        NamingConfig config = new NamingConfig();
        config.setConsumerGroupPrefixFormat("{app-or-alias}.group.");
        config.setInternalTopicPrefixFormat("{app-or-alias}.internal.");
        config.setTransactionalIdPrefixFormat("{app-or-alias}.tx.");
        config.setNormalizationStrategy(CaseStrategy.KEBAB_CASE);
        return new NamingServiceImpl(config);
    }

}
