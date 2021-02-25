package com.hermesworld.ais.galapagos.certificates.impl;

import com.hermesworld.ais.galapagos.certificates.CertificateSignResult;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CaManagerImplTest {

    private String testAppId;
    private String testAppName;
    private File f;

    @BeforeEach
    public void init() {
        Security.addProvider(new BouncyCastleProvider());
        testAppId = "four";
        testAppName = "Quattro";
        f = new File("target/temp-certificates");
        f.mkdirs();
    }

    @Test
    public void testCreateApplicationFromCsrWithValidCn() throws Exception {
        String testCsrData = StreamUtils.copyToString(
                new ClassPathResource("/certificates/test_quattroValidCn.csr").getInputStream(),
                StandardCharsets.UTF_8);
        KafkaEnvironmentConfig testKafkaEnvConfig = mockKafkaEnvironmentConfig("CN=KafkaAdmin");
        CaManagerImpl testCaManagerImpl = new CaManagerImpl(testKafkaEnvConfig, f);
        CompletableFuture<CertificateSignResult> future = testCaManagerImpl
                .createApplicationCertificateFromCsr(testAppId, testCsrData, testAppName);
        CertificateSignResult result = future.get();
        assertFalse(future.isCompletedExceptionally());
        assertFalse(future.isCancelled());
        assertNotNull(result.getDn());
    }

    @Test
    public void testCreateApplicationFromCsrWithInvalidCn() throws Exception {
        String testCsrData = StreamUtils.copyToString(
                new ClassPathResource("/certificates/test_quattroInvalidCn.csr").getInputStream(),
                StandardCharsets.UTF_8);

        KafkaEnvironmentConfig testKafkaEnvConfig = mockKafkaEnvironmentConfig("CN=KafkaAdmin");
        CaManagerImpl testCaManagerImpl = new CaManagerImpl(testKafkaEnvConfig, f);
        try {
            testCaManagerImpl.createApplicationCertificateFromCsr(testAppId, testCsrData, testAppName).get();
            fail("Expected exception has not been thrown");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof CertificateParsingException);
        }
    }

    @Test
    public void testCreateApplicationFromCsrWithInvalidAppId() throws Exception {
        String testCsrData = StreamUtils.copyToString(
                new ClassPathResource("/certificates/test_quattroInvalidAppId.csr").getInputStream(),
                StandardCharsets.UTF_8);

        KafkaEnvironmentConfig testKafkaEnvConfig = mockKafkaEnvironmentConfig("CN=KafkaAdmin");
        CaManagerImpl testCaManagerImpl = new CaManagerImpl(testKafkaEnvConfig, f);
        try {
            testCaManagerImpl.createApplicationCertificateFromCsr(testAppId, testCsrData, testAppName).get();
            fail("Expected exception has not been thrown");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof CertificateParsingException);
        }
    }

    @Test
    public void testCreateApplicationFromInvalidCsr() throws Exception {
        KafkaEnvironmentConfig testKafkaEnvConfig = mockKafkaEnvironmentConfig("CN=KafkaAdmin");
        CaManagerImpl testCaManagerImpl = new CaManagerImpl(testKafkaEnvConfig, f);
        try {
            testCaManagerImpl.createApplicationCertificateFromCsr(testAppId, "testCsrData", testAppName).get();
            fail("Expected exception has not been thrown");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof CertificateException);
        }
    }

    @Test
    public void testExtendCertificate() throws Exception {
        KafkaEnvironmentConfig testKafkaEnvConfig = mockKafkaEnvironmentConfig("CN=KafkaAdmin");
        CaManagerImpl testCaManagerImpl = new CaManagerImpl(testKafkaEnvConfig, f);

        String testCsrData = StreamUtils.copyToString(
                new ClassPathResource("/certificates/test_quattroExtend.csr").getInputStream(), StandardCharsets.UTF_8);
        CompletableFuture<CertificateSignResult> future = testCaManagerImpl
                .extendApplicationCertificate("CN=quattro,OU=certification_12345", testCsrData);
        CertificateSignResult result = future.get();

        assertEquals("CN=quattro,OU=certification_12345", result.getDn());

        // to be VERY sure, also inspect certificate (note that toString() output is slightly different)
        assertEquals("CN=quattro, OU=certification_12345", result.getCertificate().getSubjectDN().toString());
    }

    @Test
    public void testExtendCertificate_wrongDn() throws Exception {
        KafkaEnvironmentConfig testKafkaEnvConfig = mockKafkaEnvironmentConfig("CN=KafkaAdmin");
        CaManagerImpl testCaManagerImpl = new CaManagerImpl(testKafkaEnvConfig, f);

        String testCsrData = StreamUtils.copyToString(
                new ClassPathResource("/certificates/test_quattroExtend.csr").getInputStream(), StandardCharsets.UTF_8);
        try {
            testCaManagerImpl.extendApplicationCertificate("CN=quattro", testCsrData).get();
            fail("Expected exception has not been thrown");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof CertificateException);
        }
    }

    private KafkaEnvironmentConfig mockKafkaEnvironmentConfig(String clientDn) {
        KafkaEnvironmentConfig testKafkaEnvConfig = mock(KafkaEnvironmentConfig.class);
        when(testKafkaEnvConfig.getCaCertificateFile()).thenReturn(new ClassPathResource("/certificates/ca.cer"));
        when(testKafkaEnvConfig.getCaKeyFile()).thenReturn(new ClassPathResource("/certificates/ca.key"));
        when(testKafkaEnvConfig.getClientDn()).thenReturn(clientDn);
        when(testKafkaEnvConfig.getId()).thenReturn("test");
        return testKafkaEnvConfig;
    }

}
