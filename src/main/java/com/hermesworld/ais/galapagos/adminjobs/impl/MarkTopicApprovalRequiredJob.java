package com.hermesworld.ais.galapagos.adminjobs.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.hermesworld.ais.galapagos.adminjobs.AdminJob;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component
public class MarkTopicApprovalRequiredJob implements AdminJob {

    private final KafkaClusters kafkaClusters;

    private final TopicService topicService;

    public MarkTopicApprovalRequiredJob(KafkaClusters kafkaClusters,
            @Qualifier("nonvalidating") TopicService topicService) {
        this.kafkaClusters = kafkaClusters;
        this.topicService = topicService;
    }

    @Override
    public String getJobName() {
        return "mark-topic-approval-required";
    }

    @Override
    public void run(ApplicationArguments allArguments) throws Exception {
        List<String> topicNames = Optional.ofNullable(allArguments.getOptionValues("topic.name")).orElse(List.of());

        if (topicNames.isEmpty()) {
            throw new IllegalArgumentException("Please provide at least one --topic.name=<topicname> parameter");
        }

        // give Kafka some time to fill service metadata repository
        System.out.println("Waiting for metadata to be loaded...");
        Thread.sleep(10000);

        List<String> resultLines = new ArrayList<>();

        for (String topicName : topicNames) {
            for (String environmentId : kafkaClusters.getEnvironmentIds()) {
                if (topicService.getTopic(environmentId, topicName).isPresent()) {
                    topicService.setSubscriptionApprovalRequiredFlag(environmentId, topicName, true).get();
                    resultLines.add("Topic " + topicName + " reconfigured on environment " + environmentId);
                }
            }
        }

        if (resultLines.isEmpty()) {
            throw new IllegalStateException("Could not find any of the specified topics on any environment");
        }

        System.out.println();
        System.out.println("============================ Topic(s) reconfigured ===========================");
        System.out.println();
        resultLines.forEach(System.out::println);
        System.out.println();
        System.out.println("==============================================================================");
    }
}
