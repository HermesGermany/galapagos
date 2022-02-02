package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.adminjobs.AdminJob;
import com.hermesworld.ais.galapagos.devcerts.DeveloperCertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component
public class CleanupDeveloperCertificatesJob implements AdminJob {

    private final DeveloperCertificateService developerCertificateService;

    @Autowired
    public CleanupDeveloperCertificatesJob(DeveloperCertificateService developerCertificateService) {
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

        System.out.println("========================= Cleanup of total "
                + developerCertificateService.clearExpiredDeveloperCertificatesOnAllClusters().get()
                + " expired Developer Certificates on all Kafka clusters was successful ========================");
    }

}
