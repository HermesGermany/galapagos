package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.adminjobs.AdminJob;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import org.springframework.boot.ApplicationArguments;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Abstract base class for admin jobs operating on a single Kafka cluster. It deals with extracting the Kafka cluster
 * information from the admin job parameter <code>kafka.environment</code>, and handles invalid or missing parameter
 * values. Subclasses will receive the parsed and looked up Kafka cluster object, but can parse other command line
 * arguments theirselves, if needed.
 */
public abstract class SingleClusterAdminJob implements AdminJob {

    protected final KafkaClusters kafkaClusters;

    protected SingleClusterAdminJob(KafkaClusters kafkaClusters) {
        this.kafkaClusters = kafkaClusters;
    }

    @Override
    public final void run(ApplicationArguments allArguments) throws Exception {
        String kafkaEnvironment = Optional.ofNullable(allArguments.getOptionValues("kafka.environment"))
                .flatMap(ls -> ls.stream().findFirst()).orElse(null);

        if (!StringUtils.hasLength(kafkaEnvironment)) {
            throw new IllegalArgumentException(
                    "Please provide --kafka.environment=<id> to specify Kafka Environment to update application ACLs on.");
        }

        KafkaCluster cluster = kafkaClusters.getEnvironment(kafkaEnvironment).orElse(null);
        if (cluster == null) {
            throw new IllegalArgumentException("No Kafka Environment with ID " + kafkaEnvironment + " found");
        }

        runOnCluster(cluster, allArguments);
    }

    protected abstract void runOnCluster(KafkaCluster cluster, ApplicationArguments allArguments) throws Exception;
}
