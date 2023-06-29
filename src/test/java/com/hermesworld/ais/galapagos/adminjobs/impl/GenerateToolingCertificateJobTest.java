package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationConfig;
import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentsConfig;
import com.hermesworld.ais.galapagos.kafka.util.AclSupport;
import com.hermesworld.ais.galapagos.naming.ApplicationPrefixes;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.util.CertificateUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerateToolingCertificateJobTest {

    @Mock
    private KafkaClusters kafkaClusters;

    @Mock
    private KafkaCluster testCluster;

    @Mock
    private KafkaEnvironmentsConfig kafkaConfig;

    @Mock
    private NamingService namingService;

    @Mock
    private AclSupport aclSupport;

    private final File testFile = new File("target/test.p12");

    private ByteArrayOutputStream stdoutData;

    private PrintStream oldOut;

    private static final String DATA_MARKER = "CERTIFICATE DATA: ";

    @BeforeEach
    void feedMocks() throws Exception {
        Security.setProperty("crypto.policy", "unlimited");
        Security.addProvider(new BouncyCastleProvider());

        when(testCluster.getId()).thenReturn("test");
        when(testCluster.updateUserAcls(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));

        KafkaEnvironmentConfig config = mock(KafkaEnvironmentConfig.class);
        when(config.getAuthenticationMode()).thenReturn("certificates");
        when(kafkaClusters.getEnvironmentMetadata("test")).thenReturn(Optional.of(config));

        CertificatesAuthenticationConfig certConfig = new CertificatesAuthenticationConfig();
        certConfig.setApplicationCertificateValidity("P30D");
        certConfig.setCaCertificateFile(new ClassPathResource("/certificates/ca.cer"));
        certConfig.setCaKeyFile(new ClassPathResource("/certificates/ca.key"));
        certConfig.setCertificatesWorkdir("target/certificates");
        certConfig.setClientDn("cn=galapagos_test_user");

        KafkaAuthenticationModule authModule = new CertificatesAuthenticationModule("test", certConfig);
        authModule.init().get();
        when(kafkaClusters.getAuthenticationModule("test")).thenReturn(Optional.of(authModule));

        when(kafkaConfig.getMetadataTopicsPrefix()).thenReturn("galapagos.testing.");

        ApplicationPrefixes testPrefixes = mock(ApplicationPrefixes.class);
        lenient().when(testPrefixes.getInternalTopicPrefixes()).thenReturn(List.of("test.galapagos.internal."));
        when(testPrefixes.getTransactionIdPrefixes()).thenReturn(List.of("test.galapagos.internal."));
        when(testPrefixes.getConsumerGroupPrefixes()).thenReturn(List.of("galapagos."));
        when(namingService.getAllowedPrefixes(any())).thenReturn(testPrefixes);

        lenient().when(aclSupport.getRequiredAclBindings(any(), any(), any(), anyBoolean())).thenReturn(List.of());

        // redirect STDOUT to String
        oldOut = System.out;
        stdoutData = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdoutData));
    }

    @AfterEach
    void cleanup() {
        // noinspection ResultOfMethodCallIgnored
        testFile.delete();
        System.setOut(oldOut);
    }

    @Test
    void testStandard() throws Exception {
        GenerateToolingCertificateJob job = new GenerateToolingCertificateJob(kafkaClusters, aclSupport, namingService,
                kafkaConfig);

        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("output.filename")).thenReturn(List.of(testFile.getPath()));
        when(args.getOptionValues("kafka.environment")).thenReturn(List.of("test"));

        job.run(args);

        FileInputStream fis = new FileInputStream(testFile);
        byte[] readData = StreamUtils.copyToByteArray(fis);

        X509Certificate cert = extractCertificate(readData);
        assertEquals("galapagos", CertificateUtil.extractCn(cert.getSubjectDN().getName()));

        // and no data on STDOUT
        assertFalse(stdoutData.toString().contains(DATA_MARKER));

        // verify that correct internal prefix has been used (from config!)
        ArgumentCaptor<KafkaUser> userCaptor = ArgumentCaptor.forClass(KafkaUser.class);
        ArgumentCaptor<ApplicationMetadata> captor = ArgumentCaptor.forClass(ApplicationMetadata.class);
        verify(testCluster, times(1)).updateUserAcls(userCaptor.capture());
        userCaptor.getValue().getRequiredAclBindings();
        verify(aclSupport, atLeast(1)).getRequiredAclBindings(eq("test"), captor.capture(), any(), anyBoolean());

        assertEquals(1, captor.getValue().getInternalTopicPrefixes().size());
        assertEquals("galapagos.testing.", captor.getValue().getInternalTopicPrefixes().get(0));
    }

    @Test
    void testDataOnStdout() throws Exception {
        GenerateToolingCertificateJob job = new GenerateToolingCertificateJob(kafkaClusters, aclSupport, namingService,
                kafkaConfig);

        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("kafka.environment")).thenReturn(List.of("test"));

        job.run(args);

        // data must be on STDOUT
        String stdout = stdoutData.toString();
        assertTrue(stdout.contains(DATA_MARKER));

        String line = stdout.substring(stdout.indexOf(DATA_MARKER));
        line = line.substring(DATA_MARKER.length(), line.indexOf('\n'));

        // Windows hack
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }

        byte[] readData = Base64.getDecoder().decode(line);
        X509Certificate cert = extractCertificate(readData);
        assertEquals("galapagos", CertificateUtil.extractCn(cert.getSubjectDN().getName()));
    }

    private X509Certificate extractCertificate(byte[] p12Data)
            throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        KeyStore p12 = KeyStore.getInstance("pkcs12");
        p12.load(new ByteArrayInputStream(p12Data), "changeit".toCharArray());
        Enumeration<String> e = p12.aliases();
        X509Certificate cert = null;
        while (e.hasMoreElements()) {
            String alias = e.nextElement();
            if (cert != null) {
                throw new IllegalStateException("More than one certificate in .p12 data");
            }
            cert = (X509Certificate) p12.getCertificate(alias);
        }
        return cert;
    }

}
