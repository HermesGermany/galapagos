package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermesworld.ais.galapagos.adminjobs.AdminJob;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.util.HasKey;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.Collection;
import java.util.Optional;

@Component
public class CreateBackupJob implements AdminJob {

    private final KafkaClusters kafkaClusters;

    private final ObjectMapper objectMapper = JsonUtil.newObjectMapper();

    public CreateBackupJob(KafkaClusters kafkaClusters) {
        this.kafkaClusters = kafkaClusters;
    }

    @Override
    public String getJobName() {
        return "create-backup";
    }

    @Override
    public void run(ApplicationArguments allArguments) throws Exception {
        boolean createBackupFile = Optional.ofNullable(allArguments.getOptionValues("create.backup.file"))
                .flatMap(ls -> ls.stream().findFirst()).map(Boolean::parseBoolean).orElse(false);

        String outputFileName = Optional.ofNullable(allArguments.getOptionValues("output.filename"))
                .flatMap(ls -> ls.stream().findFirst()).map(String::new).orElse("backup.json");

        JSONObject backup = new JSONObject();

        System.out.println();
        System.out.println("========================= Starting Backup Creation ========================");
        System.out.println();

        kafkaClusters.getEnvironmentIds().forEach(envId -> kafkaClusters.getEnvironment(envId)
                .ifPresent(env -> backup.put(envId, backupEnvironment(env))));

        System.out.println();
        System.out.println("========================= Backup Creation COMPLETE ========================");
        System.out.println();

        if (!createBackupFile) {
            System.out.println("Backup JSON:");
            System.out.println();
            System.out.println(backup.toString(2));
        }

        if (createBackupFile) {
            System.out.println("========================= Generating Backup file as json ========================");
            File file = new File(outputFileName);

            try (Writer writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(backup.toString(2));
            }
            catch (IOException e) {
                System.err.println("Could not create Backup file");
                e.printStackTrace();
                return;
            }

            System.out.println("========================= Generated Backup file as json in " + outputFileName
                    + " ========================");

        }

    }

    private JSONObject backupEnvironment(KafkaCluster cluster) {
        JSONObject result = new JSONObject();

        for (TopicBasedRepository<?> backupTopic : cluster.getRepositories()) {
            result.put(backupTopic.getTopicName(),
                    backupTopicData(cluster.getRepository(backupTopic.getTopicName(), backupTopic.getValueClass())));
        }

        return result;
    }

    private JSONObject backupTopicData(TopicBasedRepository<? extends HasKey> repo) {
        JSONObject result = new JSONObject();
        Collection<? extends HasKey> o = repo.getObjects();
        for (HasKey obj : o) {
            try {
                result.put(obj.key(), new JSONObject(objectMapper.writeValueAsString(obj)));
            }
            catch (JSONException | JsonProcessingException e) {
                e.printStackTrace();
            }
        }
        return result;
    }
}
