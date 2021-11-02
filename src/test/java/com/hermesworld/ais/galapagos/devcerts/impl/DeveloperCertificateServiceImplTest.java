package com.hermesworld.ais.galapagos.devcerts.impl;

public class DeveloperCertificateServiceImplTest {
//
//    private List<InvocationOnMock> updateAclCalls;
//    private List<InvocationOnMock> removeAclCalls;
//
//    private long expiryDate;
//
//    private TopicBasedRepositoryMock<DevCertificateMetadata> repository;
//
//    private DeveloperCertificateServiceImpl service;
//
//    @Before
//    public void initMocks() {
//        KafkaClusters clusters = mock(KafkaClusters.class);
//        CurrentUserService userService = mock(CurrentUserService.class);
//        DevUserAclListener aclUpdater = mock(DevUserAclListener.class);
//
//        when(userService.getCurrentUserName()).thenReturn(Optional.of("testuser"));
//
//        updateAclCalls = new ArrayList<>();
//        removeAclCalls = new ArrayList<>();
//
//        when(aclUpdater.updateAcls(any(), any())).then(inv -> {
//            updateAclCalls.add(inv);
//            return FutureUtil.noop();
//        });
//
//        when(aclUpdater.removeAcls(any(), any())).then(inv -> {
//            removeAclCalls.add(inv);
//            return FutureUtil.noop();
//        });
//
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
//        KafkaCluster cluster = mock(KafkaCluster.class);
//        when(clusters.getEnvironment("_test")).thenReturn(Optional.of(cluster));
//
//        repository = new TopicBasedRepositoryMock<>();
//        when(cluster.getRepository("devcerts", DevCertificateMetadata.class)).thenReturn(repository);
//
//        service = new DeveloperCertificateServiceImpl(clusters, userService, certificateService, aclUpdater,
//                mock(TimeService.class));
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
//    }
//
}
