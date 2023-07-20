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
import org.json.JSONObject;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Admin job to generate a "tooling" API Key for one of the Kafka Clusters configured for Galapagos. <br>
 * Such "tooling" API Key is e.g. required for operating the Galapagos LeanIX synchronizer microservice. <br>
 * The job has one parameter:
 * <ul>
 * <li><code>--kafka.environment=<i>&lt;id></i> - The ID of the Kafka Environment to generate the API Key for, as
 * configured for Galapagos.</li>
 * </ul>
 * Note that this job can only generate API Keys for environments using <code>ccloud</code> authentication mode.
 *
 * @author PolatEmr
 *
 */

@Component
public class GenerateToolingApiKeyJob extends SingleClusterAdminJob {

    private final NamingService namingService;

    private final KafkaEnvironmentsConfig kafkaConfig;

    private final AclSupport aclSupport;

    public GenerateToolingApiKeyJob(KafkaClusters kafkaClusters, AclSupport aclSupport, NamingService namingService,
            KafkaEnvironmentsConfig kafkaConfig) {
        super(kafkaClusters);
        this.aclSupport = aclSupport;
        this.namingService = namingService;
        this.kafkaConfig = kafkaConfig;
    }

    @Override
    public String getJobName() {
        return "generate-galapagos-tooling-apikey";
    }

    @Override
    public void runOnCluster(KafkaCluster cluster, ApplicationArguments allArguments) throws Exception {

        KafkaEnvironmentConfig metadata = kafkaClusters.getEnvironmentMetadata(cluster.getId()).orElseThrow();
        if (!"ccloud".equals(metadata.getAuthenticationMode())) {
            throw new IllegalStateException("Environment " + cluster.getId()
                    + " does not use API Keys for authentication. Cannot generate tooling API Key.");
        }

        KafkaAuthenticationModule authModule = kafkaClusters.getAuthenticationModule(cluster.getId()).orElseThrow();

        CreateAuthenticationResult result = authModule
                .createApplicationAuthentication("galapagos", "galapagos", new JSONObject()).get();

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

        System.out.println();
        String samlUsername = new JSONObject(toolMetadata.getAuthenticationJson()).getString("apiKey");
        String secret = new String(result.getPrivateAuthenticationData());
        System.out.println("SAML Username: " + samlUsername);
        System.out.println("Secret: " + secret);

        System.out.println();
        System.out.println("==================== Galapagos Tooling API Key CREATED ====================");
        System.out.println();

        System.out.println("You can now use the API Key above for Galapagos external tooling on " + metadata.getName());

        System.out.println();
        System.out.println("Use bootstrap servers " + metadata.getBootstrapServers());
        System.out.println();
        System.out.println("The following Kafka prefixes can be used and accessed, using the API Key: ");
        System.out.println();
        System.out.println("Internal Topics: " + toolMetadata.getInternalTopicPrefixes().get(0) + "*");
        System.out.println("Consumer Groups: " + toolMetadata.getConsumerGroupPrefixes().get(0) + "*");
        System.out.println("Transactional IDs: " + toolMetadata.getTransactionIdPrefixes().get(0) + "*");
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println(
                "To remove ACLs for this API Key AND to delete the key itself, run Galapagos admin task galapagos.jobs.delete-apikey");
        System.out.println("with --kafka.environment=" + cluster.getId());
        System.out.println();
        System.out.println("==============================================================================");
    }
}
