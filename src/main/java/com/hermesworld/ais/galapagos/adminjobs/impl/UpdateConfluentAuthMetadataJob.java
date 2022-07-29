package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.adminjobs.AdminJob;
import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ServiceAccountInfo;
import com.hermesworld.ais.galapagos.ccloud.auth.ConfluentCloudAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class UpdateConfluentAuthMetadataJob implements AdminJob {

    private final KafkaClusters kafkaClusters;

    private final ApplicationsService applicationsService;

    @Autowired
    public UpdateConfluentAuthMetadataJob(KafkaClusters kafkaClusters, ApplicationsService applicationsService) {
        this.kafkaClusters = kafkaClusters;
        this.applicationsService = applicationsService;
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

            ConfluentCloudAuthenticationModule confluentCloudAuthenticationModule = (ConfluentCloudAuthenticationModule) authenticationModule;

            List<ApplicationMetadata> allApplicationMetadata = applicationsService
                    .getAllApplicationMetadata(environmentId);

            for (ApplicationMetadata app : allApplicationMetadata) {
                JSONObject authenticationJson = new JSONObject(app.getAuthenticationJson());
                Optional<ServiceAccountInfo> serviceAccountInfo = confluentCloudAuthenticationModule
                        .findServiceAccountForApp(app.getApplicationId()).get();

                if (!authenticationJson.has("serviceAccountId") && serviceAccountInfo.isPresent()) {
                    authenticationJson.put("serviceAccountId", serviceAccountInfo.get().getResourceId());
                    app.setAuthenticationJson(authenticationJson.toString());
                    KafkaCluster cluster = kafkaClusters.getEnvironment(environmentId).orElseThrow();
                    cluster.getRepository("application-metadata", ApplicationMetadata.class).save(app).get();
                }

            }

        }
    }
}
