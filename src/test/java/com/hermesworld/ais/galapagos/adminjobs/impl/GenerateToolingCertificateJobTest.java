package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.impl.UpdateApplicationAclsListener;
import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationConfig;
import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentsConfig;
import com.hermesworld.ais.galapagos.naming.ApplicationPrefixes;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.util.CertificateUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GenerateToolingCertificateJobTest {

    private KafkaClusters kafkaClusters;

    private KafkaEnvironmentsConfig kafkaConfig;

    private NamingService namingService;

    private UpdateApplicationAclsListener aclListener;

    private final File testFile = new File("target/test.p12");

    private ByteArrayOutputStream stdoutData;

    private PrintStream oldOut;

    private static final String DATA_MARKER = "CERTIFICATE DATA: ";

    @Before
    public void feedMocks() throws Exception {
        Security.setProperty("crypto.policy", "unlimited");
        Security.addProvider(new BouncyCastleProvider());

        kafkaClusters = mock(KafkaClusters.class);

        KafkaCluster testCluster = mock(KafkaCluster.class);
        when(testCluster.getId()).thenReturn("test");
        when(testCluster.updateUserAcls(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));

        KafkaEnvironmentConfig config = mock(KafkaEnvironmentConfig.class);
        when(config.getAuthenticationMode()).thenReturn("certificates");
        when(kafkaClusters.getEnvironmentMetadata("test")).thenReturn(Optional.of(config));

        CertificatesAuthenticationConfig certConfig = new CertificatesAuthenticationConfig();
        certConfig.setApplicationCertificateValidity("P30D");
        certConfig.setCaCertificateFile(new FileSystemResource("src/test/resources/certificates/ca.cer"));
        certConfig.setCaKeyFile(new FileSystemResource("src/test/resources/certificates/ca.key"));
        certConfig.setCertificatesWorkdir("target/certificates");
        certConfig.setClientDn("cn=galapagos_test_user");

        KafkaAuthenticationModule authModule = new CertificatesAuthenticationModule("test", certConfig);
        authModule.init().get();
        when(kafkaClusters.getAuthenticationModule("test")).thenReturn(Optional.of(authModule));

        kafkaConfig = mock(KafkaEnvironmentsConfig.class);
        when(kafkaConfig.getMetadataTopicsPrefix()).thenReturn("galapagos.testing.");

        namingService = mock(NamingService.class);

        ApplicationPrefixes testPrefixes = mock(ApplicationPrefixes.class);
        when(testPrefixes.getInternalTopicPrefixes()).thenReturn(List.of("test.galapagos.internal."));
        when(testPrefixes.getTransactionIdPrefixes()).thenReturn(List.of("test.galapagos.internal."));
        when(testPrefixes.getConsumerGroupPrefixes()).thenReturn(List.of("galapagos."));
        when(namingService.getAllowedPrefixes(any())).thenReturn(testPrefixes);

        aclListener = new UpdateApplicationAclsListener(kafkaClusters, null, null, null) {
            @Override
            public KafkaUser getApplicationUser(ApplicationMetadata metadata, String environmentId) {
                return null;
            }
        };

        // redirect STDOUT to String
        oldOut = System.out;
        stdoutData = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdoutData));
    }

    @After
    public void cleanup() {
        // noinspection ResultOfMethodCallIgnored
        testFile.delete();
        System.setOut(oldOut);
    }

    @Test
    public void testStandard() throws Exception {
        GenerateToolingCertificateJob job = new GenerateToolingCertificateJob(kafkaClusters, aclListener, namingService,
                kafkaConfig);

        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("output.filename")).thenReturn(Collections.singletonList(testFile.getPath()));
        when(args.getOptionValues("kafka.environment")).thenReturn(Collections.singletonList("test"));

        job.run(args);

        FileInputStream fis = new FileInputStream(testFile);
        byte[] readData = StreamUtils.copyToByteArray(fis);

        X509Certificate cert = extractCertificate(readData);
        assertEquals("galapagos", CertificateUtil.extractCn(cert.getSubjectDN().getName()));

        // and no data on STDOUT
        assertFalse(stdoutData.toString().contains(DATA_MARKER));
    }

    @Test
    public void testDataOnStdout() throws Exception {
        GenerateToolingCertificateJob job = new GenerateToolingCertificateJob(kafkaClusters, aclListener, namingService,
                kafkaConfig);

        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("kafka.environment")).thenReturn(Collections.singletonList("test"));

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
