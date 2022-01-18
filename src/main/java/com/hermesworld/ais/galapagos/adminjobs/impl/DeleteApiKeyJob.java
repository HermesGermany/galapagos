package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.impl.UpdateApplicationAclsListener;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Admin job to delete a "tooling" API Key for one of the Kafka Clusters configured for Galapagos. <br>
 * Such a key can be generated using <code>GenerateToolingApiKeyJob</code>. <br>
 * The job has three parameters:
 * <ul>
 * <li><code>--kafka.environment=<i>&lt;id></i> - The ID of the Kafka Environment to generate the API Key for, as
 * configured for Galapagos.</li>
 * <li><code>--api.key=<i>&lt;key></i> - The API Key to be deleted.</li>
 *  <li><code>--user.id=<i>&lt;id></i> - The ID of the user which can be taken from the output of 
 *  <code>GenerateToolingApiKeyJob</code>.</li>
 * </ul>
 * Note that this job can only be used for environments using <code>ccloud</code> authentication mode.
 *
 * @author PolatEmr
 *
 */

@Component
public class DeleteApiKeyJob extends SingleClusterAdminJob {
    private final UpdateApplicationAclsListener aclUpdater;

    @Autowired
    public DeleteApiKeyJob(KafkaClusters kafkaClusters, UpdateApplicationAclsListener aclUpdater) {
        super(kafkaClusters);
        this.aclUpdater = aclUpdater;
    }

    @Override
    public String getJobName() {
        return "delete-apikey";
    }

    @Override
    public void runOnCluster(KafkaCluster cluster, ApplicationArguments allArguments) throws Exception {
        String apiKey = Optional.ofNullable(allArguments.getOptionValues("api.key"))
                .flatMap(ls -> ls.stream().findFirst()).map(String::new).orElse(null);

        if (apiKey == null) {
            throw new IllegalArgumentException("Please provide a key using --api.key option");
        }

        String kafkaEnvId = Optional.ofNullable(allArguments.getOptionValues("kafka.environment"))
                .flatMap(ls -> ls.stream().findFirst()).map(String::new).orElse(null);

        if (kafkaEnvId == null) {
            throw new IllegalArgumentException("Please provide an environment using --kafka.environment option");
        }

        String userId = Optional.ofNullable(allArguments.getOptionValues("user.id"))
                .flatMap(ls -> ls.stream().findFirst()).map(String::new).orElse(null);

        if (userId == null) {
            throw new IllegalArgumentException("Please provide a user id using --user.id option");
        }

        KafkaEnvironmentConfig metadata = kafkaClusters.getEnvironmentMetadata(kafkaEnvId).orElseThrow();
        if (!"ccloud".equals(metadata.getAuthenticationMode())) {
            throw new IllegalStateException("Environment " + kafkaEnvId
                    + " does not use API Keys for authentication. Cannot generate tooling API Key.");
        }

        System.out.println("==================== Galapagos Tooling API Key DELETING ====================");

        KafkaAuthenticationModule authModule = kafkaClusters.getAuthenticationModule(kafkaEnvId).orElseThrow();

        JSONObject authJson = new JSONObject(String.format("{apiKey:%s,userId:%s}", apiKey, userId));

        ApplicationMetadata app = new ApplicationMetadata();
        app.setApplicationId("galapagos_tooling");
        app.setAuthenticationJson(authJson.toString());

        KafkaUser kafkaUser = aclUpdater.getApplicationUser(app, kafkaEnvId);
        authModule.deleteApplicationAuthentication("galapagos_tooling", authJson).get();
        cluster.updateUserAcls(kafkaUser).get();

        System.out.println(
                "==================== Galapagos Tooling API Key and corresponding ACLs DELETED ====================");

    }

}
