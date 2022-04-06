package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentsConfig;
import com.hermesworld.ais.galapagos.kafka.util.AclSupport;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.tooling.ToolingAuthenticationMetadata;
import com.hermesworld.ais.galapagos.tooling.impl.ToolingAuthenticationServiceImpl;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

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

    private final ToolingAuthenticationServiceImpl toolingAuthenticationService;

    @Autowired
    public GenerateToolingApiKeyJob(KafkaClusters kafkaClusters, AclSupport aclSupport, NamingService namingService,
            KafkaEnvironmentsConfig kafkaConfig, ToolingAuthenticationServiceImpl toolingAuthenticationService) {
        super(kafkaClusters);
        this.aclSupport = aclSupport;
        this.namingService = namingService;
        this.kafkaConfig = kafkaConfig;
        this.toolingAuthenticationService = toolingAuthenticationService;
    }

    @Override
    public String getJobName() {
        return "generate-galapagos-tooling-apikey";
    }

    @Override
    public void runOnCluster(KafkaCluster cluster, ApplicationArguments allArguments) throws Exception {
        String applicationName = Optional.ofNullable(allArguments.getOptionValues("app.name"))
                .flatMap(ls -> ls.stream().findFirst()).orElse("");

        if (applicationName.equals("")) {
            throw new IllegalStateException("Please provide application name using app.name option.");
        }

        KafkaEnvironmentConfig metadata = kafkaClusters.getEnvironmentMetadata(cluster.getId()).orElseThrow();
        if (!"ccloud".equals(metadata.getAuthenticationMode())) {
            throw new IllegalStateException("Environment " + cluster.getId()
                    + " does not use API Keys for authentication. Cannot generate tooling API Key.");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ToolingAuthenticationMetadata tooling = toolingAuthenticationService
                .createToolingAuthentication(cluster.getId(), baos, namingService.normalize(applicationName)).get();

        System.out.println();
        String samlUsername = new JSONObject(tooling.getAuthenticationJson()).getString("apiKey");
        System.out.println("SAML Username: " + samlUsername);
        System.out.println("Secret: " + baos);

        System.out.println();
        System.out.println("==================== Galapagos Tooling API Key CREATED ====================");
        System.out.println();

        System.out.println("You can now use the API Key above for Galapagos external tooling on " + metadata.getName());

        System.out.println();
        System.out.println("Use bootstrap servers " + metadata.getBootstrapServers());
        System.out.println();
        System.out.println("The following Kafka prefixes can be used and accessed, using the API Key: ");
        System.out.println();
        System.out.println("Internal Topics: " + "galapagos.internal." + "*");
        System.out.println("Consumer Groups: " + "de.hlg.galapagos." + "*");
        System.out.println("Transactional IDs: " + "galapagos.internal." + "*");
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
