package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.util.HasKey;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
public class RewriteGalapagosTopicsJob extends SingleClusterAdminJob {

    public RewriteGalapagosTopicsJob(KafkaClusters kafkaClusters) {
        super(kafkaClusters);
    }

    @Override
    protected void runOnCluster(KafkaCluster cluster, ApplicationArguments allArguments) throws Exception {
        printBanner("Rewriting Galapagos Topics");

        for (TopicBasedRepository<?> repository : cluster.getRepositories()) {
            System.out.println("Rewriting topic " + repository.getTopicName());
            rewriteRepo(repository);
        }

        printBanner("Rewriting Topics COMPLETE");
    }

    private <T extends HasKey> void rewriteRepo(TopicBasedRepository<T> repository)
            throws ExecutionException, InterruptedException {
        List<T> values = new ArrayList<>(repository.getObjects());
        for (T value : values) {
            repository.save(value).get();
        }
    }

    @Override
    public String getJobName() {
        return "rewrite-galapagos-topics";
    }
}
