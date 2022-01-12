package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.applications.impl.UpdateApplicationAclsListener;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentsConfig;
import com.hermesworld.ais.galapagos.naming.NamingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component
public class DeleteApiKeyJob extends SingleClusterAdminJob {
    private final UpdateApplicationAclsListener aclUpdater;

    private final NamingService namingService;

    private final KafkaEnvironmentsConfig kafkaConfig;

    @Autowired
    public DeleteApiKeyJob(KafkaClusters kafkaClusters, UpdateApplicationAclsListener aclUpdater,
            NamingService namingService, KafkaEnvironmentsConfig kafkaConfig) {
        super(kafkaClusters);
        this.aclUpdater = aclUpdater;
        this.namingService = namingService;
        this.kafkaConfig = kafkaConfig;
    }

    @Override
    public String getJobName() {
        return "delete-apikey";
    }

    @Override
    public void runOnCluster(KafkaCluster cluster, ApplicationArguments allArguments) throws Exception {
    }
}
