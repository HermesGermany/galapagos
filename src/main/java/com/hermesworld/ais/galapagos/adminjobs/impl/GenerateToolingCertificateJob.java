package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.applications.impl.KnownApplicationImpl;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.auth.CreateAuthenticationResult;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentsConfig;
import com.hermesworld.ais.galapagos.kafka.util.AclSupport;
import com.hermesworld.ais.galapagos.naming.ApplicationPrefixes;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.util.CertificateUtil;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.json.JSONObject;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin job to generate a "tooling" certificate for one of the Kafka Clusters configured for Galapagos. <br>
 * Such "tooling" certificate is e.g. required for operating the Galapagos LeanIX synchronizer microservice. <br>
 * The job has two parameters:
 * <ul>
 * <li><code>--output.filename=<i>&lt;p12-file></i> - The name of a file to receive the generated PKCS12 keystore. If
 * not given, the PKCS12 data is written to STDOUT, Base64 encoded.</li>
 * <li><code>--kafka.environment=<i>&lt;id></i> - The ID of the Kafka Environment to generate the certificate for, as
 * configured for Galapagos.</li>
 * </ul>
 * Note that this job can only generate certificates for environments using <code>certificates</code> authentication
 * mode.
 *
 * @author AlbrechtFlo
 *
 */
@Component
public class GenerateToolingCertificateJob extends SingleClusterAdminJob {

    private final AclSupport aclSupport;

    private final NamingService namingService;

    private final KafkaEnvironmentsConfig kafkaConfig;

    public GenerateToolingCertificateJob(KafkaClusters kafkaClusters, AclSupport aclSupport,
            NamingService namingService, KafkaEnvironmentsConfig kafkaConfig) {
        super(kafkaClusters);
        this.aclSupport = aclSupport;
        this.namingService = namingService;
        this.kafkaConfig = kafkaConfig;
    }

    @Override
    public String getJobName() {
        return "generate-galapagos-tooling-certificate";
    }

    @Override
    public void runOnCluster(KafkaCluster cluster, ApplicationArguments allArguments) throws Exception {
        String outputFilename = Optional.ofNullable(allArguments.getOptionValues("output.filename"))
                .flatMap(ls -> ls.stream().findFirst()).orElse(null);

        KafkaEnvironmentConfig metadata = kafkaClusters.getEnvironmentMetadata(cluster.getId()).orElseThrow();
        if (!"certificates".equals(metadata.getAuthenticationMode())) {
            throw new IllegalStateException("Environment " + cluster.getId()
                    + " does not use certificates for authentication. Cannot generate tooling certificate.");
        }

        if (!ObjectUtils.isEmpty(outputFilename)) {
            try {
                new FileOutputStream(outputFilename).close();
            }
            catch (IOException e) {
                throw new IllegalArgumentException("Cannot write output file " + outputFilename);
            }
        }

        KafkaAuthenticationModule authModule = kafkaClusters.getAuthenticationModule(cluster.getId()).orElseThrow();

        // Create a CSR on the fly to enable certificate generation also on PROD environments
        KeyPair keyPair = CertificateUtil.generateKeyPair();
        X500Name name = CertificateUtil.uniqueX500Name("galapagos");
        PKCS10CertificationRequest request = CertificateUtil.buildCsr(name, keyPair);
        String csrData = CertificateUtil.toPemString(request);

        CreateAuthenticationResult result = authModule.createApplicationAuthentication("galapagos", "galapagos",
                new JSONObject(Map.of("generateKey", false, "csrData", csrData))).get();

        // build P12 file from certificate and private key
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) certFactory
                .generateCertificate(new ByteArrayInputStream(result.getPrivateAuthenticationData()));
        byte[] p12Data = CertificateUtil.buildPrivateKeyStore(cert, keyPair.getPrivate(), "changeit".toCharArray());

        ApplicationMetadata toolMetadata = new ApplicationMetadata();
        toolMetadata.setApplicationId("galapagos_tooling");
        toolMetadata.setAuthenticationJson(result.getPublicAuthenticationData().toString());
        KnownApplication dummyApp = new KnownApplicationImpl("galapagos", "Galapagos");

        ApplicationPrefixes prefixes = namingService.getAllowedPrefixes(dummyApp);

        // intentionally use config value here - could differ e.g. for Galapagos test instance on same cluster
        toolMetadata.setInternalTopicPrefixes(List.of(kafkaConfig.getMetadataTopicsPrefix()));
        toolMetadata.setConsumerGroupPrefixes(prefixes.getConsumerGroupPrefixes());
        toolMetadata.setTransactionIdPrefixes(prefixes.getTransactionIdPrefixes());

        cluster.updateUserAcls(new ToolingUser(toolMetadata, cluster.getId(), authModule, aclSupport)).get();

        if (!ObjectUtils.isEmpty(outputFilename)) {
            try (FileOutputStream fos = new FileOutputStream(outputFilename)) {
                fos.write(p12Data);
            }
        }
        else {
            String base64Data = Base64.getEncoder().encodeToString(p12Data);
            System.out.println("CERTIFICATE DATA: " + base64Data);
        }

        System.out.println();
        System.out.println("==================== Galapagos Tooling Certificate CREATED ====================");
        System.out.println();
        if (!ObjectUtils.isEmpty(outputFilename)) {
            System.out.println("You can now use the certificate in " + outputFilename
                    + " for Galapagos external tooling on " + metadata.getName());
        }
        else {
            System.out.println(
                    "You can now use the certificate (which is encoded above) for Galapagos external tooling on "
                            + metadata.getName());
        }
        System.out.println();
        System.out.println("Use bootstrap servers " + metadata.getBootstrapServers());
        System.out.println();
        System.out.println("The following Kafka prefixes can be used and accessed, using the certificate: ");
        System.out.println();
        System.out.println("Internal Topics: " + toolMetadata.getInternalTopicPrefixes().get(0) + "*");
        System.out.println("Consumer Groups: " + toolMetadata.getConsumerGroupPrefixes().get(0) + "*");
        System.out.println("Transactional IDs: " + toolMetadata.getTransactionIdPrefixes().get(0) + "*");
        System.out.println();
        System.out.println();
        System.out.println("The certificate expires at " + cert.getNotAfter());
        System.out.println();
        System.out.println("To remove ACLs for this certificate, run Galapagos admin task galapagos.jobs.delete-acls");
        System.out.println("with --certificate.dn=" + result.getPublicAuthenticationData().getString("dn")
                + " --kafka.environment=" + cluster.getId());
        System.out.println();
        System.out.println("==============================================================================");
    }

}
