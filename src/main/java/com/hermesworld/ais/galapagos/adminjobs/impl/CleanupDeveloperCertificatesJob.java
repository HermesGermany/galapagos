package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.adminjobs.AdminJob;
import com.hermesworld.ais.galapagos.devcerts.DevCertificateMetadata;
import com.hermesworld.ais.galapagos.devcerts.DeveloperCertificateService;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.util.TimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component
public class CleanupDeveloperCertificatesJob implements AdminJob {
    private final KafkaClusters kafkaClusters;

    private final TimeService timeService;

    private final DeveloperCertificateService developerCertificateService;

    private int numberOfCertsToBeCleaned = 0;

    @Autowired
    public CleanupDeveloperCertificatesJob(KafkaClusters kafkaClusters, TimeService timeService,
            DeveloperCertificateService developerCertificateService) {
        this.kafkaClusters = kafkaClusters;
        this.timeService = timeService;
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
            TopicBasedRepository<DevCertificateMetadata> repo = cluster.getRepository("devcerts",
                    DevCertificateMetadata.class);

            numberOfCertsToBeCleaned += (int) repo.getObjects().stream()
                    .filter(devCert -> devCert.getExpiryDate().isBefore(timeService.getTimestamp().toInstant()))
                    .count();

            developerCertificateService.clearExpiredDeveloperCertificates(repo, cluster);
        });

        System.out.println("========================= Cleanup of total " + numberOfCertsToBeCleaned
                + " expired Developer Certificates on all Kafka clusters was successful ========================");
    }

}
