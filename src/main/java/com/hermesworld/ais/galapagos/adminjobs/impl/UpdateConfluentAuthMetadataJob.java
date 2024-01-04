package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.adminjobs.AdminJob;
import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.ccloud.auth.ConfluentCloudAuthenticationModule;
import com.hermesworld.ais.galapagos.devauth.DevAuthenticationMetadata;
import com.hermesworld.ais.galapagos.devauth.DeveloperAuthenticationService;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import org.json.JSONObject;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UpdateConfluentAuthMetadataJob implements AdminJob {

    private final KafkaClusters kafkaClusters;

    private final ApplicationsService applicationsService;

    private final DeveloperAuthenticationService devAuthService;

    public UpdateConfluentAuthMetadataJob(KafkaClusters kafkaClusters, ApplicationsService applicationsService,
            DeveloperAuthenticationService devAuthService) {
        this.kafkaClusters = kafkaClusters;
        this.applicationsService = applicationsService;
        this.devAuthService = devAuthService;
    }

    @Override
    public String getJobName() {
        return "update-confluent-auth-metadata";
    }

    @Override
    public void run(ApplicationArguments allArguments) throws Exception {
        for (String environmentId : kafkaClusters.getEnvironmentIds()) {
            KafkaAuthenticationModule authenticationModule = kafkaClusters.getAuthenticationModule(environmentId)
                    .orElse(null);

            if (!(authenticationModule instanceof ConfluentCloudAuthenticationModule)) {
                continue;
            }
            KafkaCluster cluster = kafkaClusters.getEnvironment(environmentId).orElseThrow();
            TopicBasedRepository<ApplicationMetadata> appMetadataRepo = cluster.getRepository("application-metadata",
                    ApplicationMetadata.class);
            TopicBasedRepository<DevAuthenticationMetadata> devAuthRepo = cluster.getRepository("devauth",
                    DevAuthenticationMetadata.class);

            ConfluentCloudAuthenticationModule confluentCloudAuthenticationModule = (ConfluentCloudAuthenticationModule) authenticationModule;

            List<ApplicationMetadata> allApplicationMetadata = applicationsService
                    .getAllApplicationMetadata(environmentId);

            for (ApplicationMetadata app : allApplicationMetadata) {
                JSONObject authenticationJson = new JSONObject(app.getAuthenticationJson());
                JSONObject newAuthJson = confluentCloudAuthenticationModule.upgradeAuthMetadata(authenticationJson)
                        .get();

                if (!newAuthJson.toString().equals(authenticationJson.toString())) {
                    System.out.println("Upgrading authentication for app " + app.getApplicationId());
                    app.setAuthenticationJson(newAuthJson.toString());
                    appMetadataRepo.save(app).get();
                }
            }

            List<DevAuthenticationMetadata> allDevAuth = devAuthService.getAllDeveloperAuthentications(environmentId);

            for (DevAuthenticationMetadata auth : allDevAuth) {
                JSONObject authenticationJson = new JSONObject(auth.getAuthenticationJson());
                JSONObject newAuthJson = confluentCloudAuthenticationModule.upgradeAuthMetadata(authenticationJson)
                        .get();
                if (!newAuthJson.toString().equals(authenticationJson.toString())) {
                    System.out.println("Upgrading authentication for developer " + auth.getUserName());
                    auth.setAuthenticationJson(newAuthJson.toString());
                    devAuthRepo.save(auth).get();
                }
            }
        }
    }
}
