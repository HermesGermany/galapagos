package com.hermesworld.ais.galapagos.adminjobs.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.config.ApplicationsConfig;
import com.hermesworld.ais.galapagos.applications.impl.UpdateApplicationAclsListener;
import com.hermesworld.ais.galapagos.certificates.CaManager;
import com.hermesworld.ais.galapagos.certificates.CertificateSignResult;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentsConfig;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.boot.ApplicationArguments;
import org.springframework.util.StreamUtils;

public class GenerateToolingCertificateJobTest {

    private KafkaClusters kafkaClusters;

    private ApplicationsConfig applicationsConfig;

    private KafkaEnvironmentsConfig kafkaConfig;

    private UpdateApplicationAclsListener aclListener;

    private final byte[] testData = { 17, 12, 99, 42, 23 };

    private final File testFile = new File("target/test.p12");

    private ByteArrayOutputStream stdoutData;

    private PrintStream oldOut;

    private static final String DATA_MARKER = "CERTIFICATE DATA: ";

    @Before
    public void feedMocks() {
        kafkaClusters = mock(KafkaClusters.class);

        KafkaCluster testCluster = mock(KafkaCluster.class);
        when(testCluster.getId()).thenReturn("test");
        when(testCluster.updateUserAcls(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
        KafkaEnvironmentConfig config = mock(KafkaEnvironmentConfig.class);
        when(kafkaClusters.getEnvironmentMetadata("test")).thenReturn(Optional.of(config));

        CaManager caMan = mock(CaManager.class);
        when(kafkaClusters.getCaManager("test")).thenReturn(Optional.of(caMan));

        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getNotAfter()).thenReturn(new Date());
        CertificateSignResult result = new CertificateSignResult(cert, "test", "cn=test", testData);
        when(caMan.createToolingCertificateAndPrivateKey()).thenReturn(CompletableFuture.completedFuture(result));

        applicationsConfig = mock(ApplicationsConfig.class);
        kafkaConfig = mock(KafkaEnvironmentsConfig.class);
        when(kafkaConfig.getMetadataTopicsPrefix()).thenReturn("galapagos.testing.");

        aclListener = new UpdateApplicationAclsListener(null, null, null) {
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
        GenerateToolingCertificateJob job = new GenerateToolingCertificateJob(kafkaClusters, applicationsConfig,
                kafkaConfig, aclListener);

        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("output.filename")).thenReturn(Collections.singletonList(testFile.getPath()));
        when(args.getOptionValues("kafka.environment")).thenReturn(Collections.singletonList("test"));

        job.run(args);

        FileInputStream fis = new FileInputStream(testFile);
        byte[] readData = StreamUtils.copyToByteArray(fis);
        assertArrayEquals(testData, readData);

        // and no data on STDOUT
        assertFalse(new String(stdoutData.toByteArray()).contains(DATA_MARKER));
    }

    @Test
    public void testDataOnStdout() throws Exception {
        GenerateToolingCertificateJob job = new GenerateToolingCertificateJob(kafkaClusters, applicationsConfig,
                kafkaConfig, aclListener);

        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("kafka.environment")).thenReturn(Collections.singletonList("test"));

        job.run(args);

        // data must be on STDOUT
        String stdout = new String(stdoutData.toByteArray());
        assertTrue(stdout.contains(DATA_MARKER));

        String line = stdout.substring(stdout.indexOf(DATA_MARKER));
        line = line.substring(DATA_MARKER.length(), line.indexOf('\n'));

        // Windows hack
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }

        byte[] readData = Base64.getDecoder().decode(line);
        assertArrayEquals(testData, readData);
    }

}
