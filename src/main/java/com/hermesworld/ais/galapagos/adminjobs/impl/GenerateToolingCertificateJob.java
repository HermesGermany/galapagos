package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.applications.impl.KnownApplicationImpl;
import com.hermesworld.ais.galapagos.applications.impl.UpdateApplicationAclsListener;
import com.hermesworld.ais.galapagos.certificates.CaManager;
import com.hermesworld.ais.galapagos.certificates.CertificateSignResult;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.naming.ApplicationPrefixes;
import com.hermesworld.ais.galapagos.naming.NamingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
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
 *
 * @author AlbrechtFlo
 *
 */
@Component
public class GenerateToolingCertificateJob extends SingleClusterAdminJob {

    private final UpdateApplicationAclsListener aclUpdater;

    private final NamingService namingService;

    @Autowired
    public GenerateToolingCertificateJob(KafkaClusters kafkaClusters, UpdateApplicationAclsListener aclUpdater,
            NamingService namingService) {
        super(kafkaClusters);
        this.aclUpdater = aclUpdater;
        this.namingService = namingService;
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

        if (!StringUtils.isEmpty(outputFilename)) {
            try {
                new FileOutputStream(outputFilename).close();
            }
            catch (IOException e) {
                throw new IllegalArgumentException("Cannot write output file " + outputFilename);
            }
        }

        CaManager caManager = kafkaClusters.getCaManager(cluster.getId()).orElseThrow();

        CertificateSignResult result = caManager.createToolingCertificateAndPrivateKey().get();

        // create pseudo Application and ApplicationMetadata to get KafkaUser for ACL update
        KnownApplication galapagosApp = new KnownApplicationImpl("galapagos", "Galapagos");
        ApplicationPrefixes prefixes = namingService.getAllowedPrefixes(galapagosApp);
        ApplicationMetadata toolMetadata = new ApplicationMetadata();
        toolMetadata.setApplicationId("__GALAPAGOS_TOOLING__");
        toolMetadata.setInternalTopicPrefixes(prefixes.getInternalTopicPrefixes());
        toolMetadata.setConsumerGroupPrefixes(prefixes.getConsumerGroupPrefixes());
        toolMetadata.setTransactionIdPrefixes(prefixes.getTransactionIdPrefixes());
        toolMetadata.setDn(result.getDn());

        cluster.updateUserAcls(aclUpdater.getApplicationUser(toolMetadata, cluster.getId())).get();

        if (!StringUtils.isEmpty(outputFilename)) {
            try (FileOutputStream fos = new FileOutputStream(outputFilename)) {
                fos.write(result.getP12Data().orElseThrow());
            }
        }
        else {
            String base64Data = Base64.getEncoder().encodeToString(result.getP12Data().orElseThrow());
            System.out.println("CERTIFICATE DATA: " + base64Data);
        }

        System.out.println();
        System.out.println("==================== Galapagos Tooling Certificate CREATED ====================");
        System.out.println();
        if (!StringUtils.isEmpty(outputFilename)) {
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
        System.out.println("The certificate expires at " + result.getCertificate().getNotAfter());
        System.out.println();
        System.out.println("To remove ACLs for this certificate, run Galapagos admin task galapagos.jobs.delete-acls");
        System.out.println("with --certificate.dn=" + result.getDn() + " --kafka.environment=" + cluster.getId());
        System.out.println();
        System.out.println("==============================================================================");
    }
}
