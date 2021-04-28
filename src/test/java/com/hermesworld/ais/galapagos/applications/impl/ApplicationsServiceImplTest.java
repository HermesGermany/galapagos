package com.hermesworld.ais.galapagos.applications.impl;

public class ApplicationsServiceImplTest {
//
//    private KafkaClusters kafkaClusters;
//
//    private KafkaCluster testCluster;
//
//    private final TopicBasedRepository<ApplicationOwnerRequest> requestRepository = new TopicBasedRepositoryMock<>();
//
//    private final TopicBasedRepository<KnownApplicationImpl> knownApplicationRepository = new TopicBasedRepositoryMock<>();
//
//    private final TopicBasedRepository<ApplicationMetadata> applicationMetadataRepository = new TopicBasedRepositoryMock<>();
//
//    @Before
//    public void feedMocks() {
//        kafkaClusters = mock(KafkaClusters.class);
//
//        when(kafkaClusters.getGlobalRepository("application-owner-requests", ApplicationOwnerRequest.class))
//                .thenReturn(requestRepository);
//        when(kafkaClusters.getGlobalRepository("known-applications", KnownApplicationImpl.class))
//                .thenReturn(knownApplicationRepository);
//
//        testCluster = mock(KafkaCluster.class);
//        when(testCluster.getRepository("application-metadata", ApplicationMetadata.class))
//                .thenReturn(applicationMetadataRepository);
//
//        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
//    }
//
//    @Test
//    public void testRemoveOldRequests() {
//        List<ApplicationOwnerRequest> daos = new ArrayList<>();
//
//        ZonedDateTime createdAt = ZonedDateTime.of(LocalDateTime.of(2019, 1, 1, 10, 0), ZoneId.systemDefault());
//        ZonedDateTime statusChange1 = ZonedDateTime.of(LocalDateTime.of(2019, 3, 1, 10, 0), ZoneId.systemDefault());
//        ZonedDateTime statusChange2 = ZonedDateTime.of(LocalDateTime.of(2019, 3, 1, 15, 0), ZoneId.systemDefault());
//        ZonedDateTime statusChange3 = ZonedDateTime.of(LocalDateTime.of(2019, 2, 28, 15, 0), ZoneId.systemDefault());
//        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2019, 3, 31, 14, 0), ZoneId.systemDefault());
//
//        ApplicationOwnerRequest dao = createRequestDao("req1", createdAt, "testuser");
//        dao.setLastStatusChangeAt(statusChange1);
//        dao.setState(RequestState.APPROVED);
//        daos.add(dao);
//        dao = createRequestDao("req2", createdAt, "testuser");
//        dao.setLastStatusChangeAt(statusChange1);
//        dao.setState(RequestState.REJECTED);
//        daos.add(dao);
//        dao = createRequestDao("req3", createdAt, "testuser");
//        dao.setLastStatusChangeAt(statusChange2);
//        dao.setState(RequestState.REJECTED);
//        daos.add(dao);
//        dao = createRequestDao("req4", createdAt, "testuser");
//        dao.setLastStatusChangeAt(statusChange3);
//        dao.setState(RequestState.REVOKED);
//        daos.add(dao);
//        dao = createRequestDao("req5", createdAt, "testuser");
//        dao.setLastStatusChangeAt(statusChange3);
//        dao.setState(RequestState.SUBMITTED);
//        daos.add(dao);
//
//        daos.forEach(requestRepository::save);
//
//        ApplicationsServiceImpl service = new ApplicationsServiceImpl(kafkaClusters, mock(CurrentUserService.class),
//                () -> now, mock(NamingService.class), new GalapagosEventManagerMock());
//
//        service.removeOldRequests();
//
//        assertTrue(requestRepository.getObject("req2").isEmpty());
//        assertTrue(requestRepository.getObject("req4").isEmpty());
//        assertFalse(requestRepository.getObject("req1").isEmpty());
//        assertFalse(requestRepository.getObject("req3").isEmpty());
//        assertFalse(requestRepository.getObject("req5").isEmpty());
//    }
//
//    @Test
//    public void testKnownApplicationAfterSubmitting() {
//        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
//        String testUserName = "test";
//        String appId = "42";
//        ApplicationOwnerRequest appOwnReq = createRequestWithApplicationID(appId, "1", RequestState.SUBMITTED, now,
//                testUserName);
//
//        CurrentUserService currentUserService = mock(CurrentUserService.class);
//        when(currentUserService.getCurrentUserName()).thenReturn(Optional.of(appOwnReq.getUserName()));
//
//        KnownApplicationImpl knownApp = new KnownApplicationImpl(appOwnReq.getApplicationId(), "App1");
//        KnownApplicationImpl knownApp2 = new KnownApplicationImpl("43", "App2");
//
//        knownApplicationRepository.save(knownApp);
//        knownApplicationRepository.save(knownApp2);
//
//        requestRepository.save(appOwnReq);
//
//        ApplicationsServiceImpl applicationServiceImpl = new ApplicationsServiceImpl(kafkaClusters, currentUserService,
//                mock(TimeService.class), mock(NamingService.class), mock(GalapagosEventManager.class));
//        List<? extends KnownApplication> result = applicationServiceImpl.getKnownApplications(true);
//
//        for (KnownApplication resultKnownApp : result) {
//            assertNotEquals(appId, resultKnownApp.getId());
//        }
//        assertEquals(1, result.size());
//        assertEquals("43", result.get(0).getId());
//    }
//
//    @Test
//    public void testReplaceCertificate_appChanges() throws Exception {
//        // Application changed e.g. in external Architecture system
//        KnownApplicationImpl app = new KnownApplicationImpl("quattro-1", "Quattro");
//        app.setAliases(List.of("q2"));
//        knownApplicationRepository.save(app).get();
//
//        // But is already registered with Alias Q1 and associated rights
//        ApplicationMetadata appl = new ApplicationMetadata();
//        appl.setApplicationId("quattro-1");
//        appl.setDn("CN=quattro,OU=certification_12345");
//        appl.setInternalTopicPrefixes(List.of("quattro.internal.", "q1.internal."));
//        appl.setConsumerGroupPrefixes(List.of("groups.quattro.", "groups.q1."));
//        appl.setTransactionIdPrefixes(List.of("quattro.internal.", "q1.internal."));
//        applicationMetadataRepository.save(appl).get();
//
//        // The NamingService would return new rights
//        ApplicationPrefixes newPrefixes = mock(ApplicationPrefixes.class);
//        when(newPrefixes.combineWith(any())).thenCallRealMethod();
//        when(newPrefixes.getInternalTopicPrefixes()).thenReturn(List.of("quattro.internal.", "q2.internal."));
//        when(newPrefixes.getConsumerGroupPrefixes()).thenReturn(List.of("groups.quattro.", "groups.q2."));
//        when(newPrefixes.getTransactionIdPrefixes()).thenReturn(List.of("quattro.internal.", "q2.internal."));
//
//        NamingService namingService = mock(NamingService.class);
//        when(namingService.getAllowedPrefixes(any())).thenReturn(newPrefixes);
//
//        ApplicationsServiceImpl applicationServiceImpl = new ApplicationsServiceImpl(kafkaClusters,
//                mock(CurrentUserService.class), mock(TimeService.class), namingService,
//                new GalapagosEventManagerMock());
//
//        X509Certificate cert = mock(X509Certificate.class);
//        when(cert.getNotAfter()).thenReturn(new Date());
//        CaManager caMan = mock(CaManager.class);
//        when(kafkaClusters.getCaManager("test")).thenReturn(Optional.of(caMan));
//        when(caMan.createApplicationCertificateAndPrivateKey(any(), any()))
//                .thenReturn(CompletableFuture.completedFuture(
//                        new CertificateSignResult(cert, null, "CN=quattro,OU=certification_12346", new byte[0])));
//
//        applicationServiceImpl.createApplicationCertificateAndPrivateKey("test", "quattro-1",
//                new ByteArrayOutputStream());
//
//        appl = applicationMetadataRepository.getObject("quattro-1").get();
//
//        assertEquals("CN=quattro,OU=certification_12346", appl.getDn());
//
//        assertTrue(appl.getInternalTopicPrefixes().contains("quattro.internal."));
//        assertTrue(appl.getInternalTopicPrefixes().contains("q1.internal."));
//        assertTrue(appl.getInternalTopicPrefixes().contains("q2.internal."));
//        assertTrue(appl.getTransactionIdPrefixes().contains("quattro.internal."));
//        assertTrue(appl.getTransactionIdPrefixes().contains("q1.internal."));
//        assertTrue(appl.getTransactionIdPrefixes().contains("q2.internal."));
//        assertTrue(appl.getConsumerGroupPrefixes().contains("groups.quattro."));
//        assertTrue(appl.getConsumerGroupPrefixes().contains("groups.q1."));
//        assertTrue(appl.getConsumerGroupPrefixes().contains("groups.q2."));
//    }
//
//    @Test
//    public void testExtendCertificate() throws Exception {
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
//    }
//
//    private static ApplicationOwnerRequest createRequestDao(String id, ZonedDateTime createdAt, String userName) {
//        ApplicationOwnerRequest dao = new ApplicationOwnerRequest();
//        dao.setCreatedAt(createdAt);
//        dao.setId(id);
//        dao.setUserName(userName);
//        dao.setState(RequestState.SUBMITTED);
//        return dao;
//    }
//
//    private static ApplicationOwnerRequest createRequestWithApplicationID(String appID, String id,
//            RequestState reqState, ZonedDateTime createdAt, String userName) {
//        ApplicationOwnerRequest dao = new ApplicationOwnerRequest();
//        dao.setApplicationId(appID);
//        dao.setCreatedAt(createdAt);
//        dao.setId(id);
//        dao.setUserName(userName);
//        dao.setState(reqState);
//        return dao;
//
//    }

}
