package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.adminjobs.AdminJob;
import com.hermesworld.ais.galapagos.devcerts.DeveloperCertificateService;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component
public class CleanupDeveloperCertificatesJob implements AdminJob {

    private final KafkaClusters kafkaClusters;

    private final DeveloperCertificateService developerCertificateService;

    @Autowired
    public CleanupDeveloperCertificatesJob(KafkaClusters kafkaClusters,
            DeveloperCertificateService developerCertificateService) {
        this.kafkaClusters = kafkaClusters;
        this.developerCertificateService = developerCertificateService;
    }

    @Override
    public String getJobName() {
        return "cleanup-developer-certificates";
    }

    @Override
    public void run(ApplicationArguments allArguments) throws Exception {
        System.out.println();
        System.out.println(
                "========================= Starting Cleanup of expired Developer Certificates on all Kafka clusters ========================");
        System.out.println();

        kafkaClusters.getEnvironments().forEach(cluster -> {
            KafkaEnvironmentConfig metadata = kafkaClusters.getEnvironmentMetadata(cluster.getId()).orElse(null);

            if (metadata == null) {
                throw new IllegalStateException("Could no get config for Environment " + cluster.getId());
            }

            if (!"certificates".equals(metadata.getAuthenticationMode())) {
                throw new IllegalStateException(
                        "Environment " + cluster.getId() + " does not use Certificates for authentication.");
            }

        });

        System.out.println("========================= Cleanup of total "
                + developerCertificateService.clearExpiredDeveloperCertificatesOnAllClusters().get()
                + " expired Developer Certificates on all Kafka clusters was successful ========================");
    }

}
