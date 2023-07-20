package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.adminjobs.AdminJob;
import com.hermesworld.ais.galapagos.devauth.DeveloperAuthenticationService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component
public class CleanupDeveloperAuthenticationsJob implements AdminJob {

    private final DeveloperAuthenticationService developerAuthenticationService;

    public CleanupDeveloperAuthenticationsJob(DeveloperAuthenticationService developerAuthenticationService) {
        this.developerAuthenticationService = developerAuthenticationService;
    }

    @Override
    public String getJobName() {
        return "cleanup-developer-authentications";
    }

    @Override
    public void run(ApplicationArguments allArguments) throws Exception {
        System.out.println();
        System.out.println(
                "=========== Starting Cleanup of expired Developer Authentications on all Kafka clusters ===========");
        System.out.println();

        System.out.println("=========== Cleanup of total "
                + developerAuthenticationService.clearExpiredDeveloperAuthenticationsOnAllClusters().get()
                + " expired Developer Certificates on all Kafka clusters was successful ===========");
    }

}
