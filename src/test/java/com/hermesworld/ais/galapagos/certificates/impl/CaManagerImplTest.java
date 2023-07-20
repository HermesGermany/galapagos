package com.hermesworld.ais.galapagos.certificates.impl;

import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationConfig;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class CaManagerImplTest {

    private final static String testAppId = "four";

    private final static String testAppName = "Quattro";

    private final static String workdir = "target/temp-certificates";

    private CertificatesAuthenticationConfig authConfig;

    @BeforeEach
    void init() {
        Security.addProvider(new BouncyCastleProvider());
        authConfig = mockAuthConfig();
    }

    @Test
    void testCreateApplicationFromCsrWithValidCn() throws Exception {
        String testCsrData = StreamUtils.copyToString(
                new ClassPathResource("/certificates/test_quattroValidCn.csr").getInputStream(),
                StandardCharsets.UTF_8);

        CaManagerImpl testCaManagerImpl = new CaManagerImpl("test", authConfig);
        CompletableFuture<CertificateSignResult> future = testCaManagerImpl
                .createApplicationCertificateFromCsr(testAppId, testCsrData, testAppName);
        CertificateSignResult result = future.get();
        assertFalse(future.isCompletedExceptionally());
        assertFalse(future.isCancelled());
        assertNotNull(result.getDn());
    }

    @Test
    void testCreateApplicationFromCsrWithInvalidCn() throws Exception {
        String testCsrData = StreamUtils.copyToString(
                new ClassPathResource("/certificates/test_quattroInvalidCn.csr").getInputStream(),
                StandardCharsets.UTF_8);

        CaManagerImpl testCaManagerImpl = new CaManagerImpl("test", authConfig);
        try {
            testCaManagerImpl.createApplicationCertificateFromCsr(testAppId, testCsrData, testAppName).get();
            fail("Expected exception has not been thrown");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof CertificateParsingException);
        }
    }

    @Test
    void testCreateApplicationFromCsrWithInvalidAppId() throws Exception {
        String testCsrData = StreamUtils.copyToString(
                new ClassPathResource("/certificates/test_quattroInvalidAppId.csr").getInputStream(),
                StandardCharsets.UTF_8);

        CaManagerImpl testCaManagerImpl = new CaManagerImpl("test", authConfig);
        try {
            testCaManagerImpl.createApplicationCertificateFromCsr(testAppId, testCsrData, testAppName).get();
            fail("Expected exception has not been thrown");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof CertificateParsingException);
        }
    }

    @Test
    void testCreateApplicationFromInvalidCsr() throws Exception {
        CaManagerImpl testCaManagerImpl = new CaManagerImpl("test", authConfig);
        try {
            testCaManagerImpl.createApplicationCertificateFromCsr(testAppId, "testCsrData", testAppName).get();
            fail("Expected exception has not been thrown");
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof CertificateException);
        }
    }

    @Test
    void testExtendCertificate() throws Exception {
        CaManagerImpl testCaManagerImpl = new CaManagerImpl("test", authConfig);

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
    void testExtendCertificate_wrongDn() throws Exception {
        CaManagerImpl testCaManagerImpl = new CaManagerImpl("test", authConfig);

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

    private CertificatesAuthenticationConfig mockAuthConfig() {
        CertificatesAuthenticationConfig config = new CertificatesAuthenticationConfig();
        config.setClientDn("CN=KafkaAdmin");
        config.setCaCertificateFile(new ClassPathResource("/certificates/ca.cer"));
        config.setCaKeyFile(new ClassPathResource("/certificates/ca.key"));
        config.setCertificatesWorkdir(workdir);

        return config;
    }

}
