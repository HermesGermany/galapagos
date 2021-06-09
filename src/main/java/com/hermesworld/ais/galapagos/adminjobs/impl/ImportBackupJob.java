package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermesworld.ais.galapagos.adminjobs.AdminJob;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.util.HasKey;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Admin job for importing a backup into Galapagos.<br>
 * The job has two parameters:
 * <ul>
 * <li><code>--import.file=<i>&lt;json-file></i> - The name of a file from which the Objects are read and imported into
 * Galapagos.</li>
 * <li><code>--clearRepos=<i>&lt;boolean></i> - A boolean value, which indicates, whether all Repositories should be
 * emptied before performing the import. Set it to true, if Repositories should be emptied, otherwise to false, so the
 * old Objects in the Repositories will still be there and the new ones are added additionally.</li>
 * </ul>
 * 
 * @author PolatEmr
 *
 */

@Component
public class ImportBackupJob implements AdminJob {

    private final KafkaClusters kafkaClusters;

    private final ObjectMapper objectMapper;

    @Autowired
    public ImportBackupJob(KafkaClusters kafkaClusters) {
        this.kafkaClusters = kafkaClusters;
        this.objectMapper = JsonUtil.newObjectMapper();
    }

    @Override
    public String getJobName() {
        return "import-backup";
    }

    @Override
    public void run(ApplicationArguments allArguments) throws Exception {
        String jsonFile = Optional.ofNullable(allArguments.getOptionValues("import.file"))
                .flatMap(ls -> ls.stream().findFirst()).orElse(null);

        Boolean emptyRepos = Optional.ofNullable(allArguments.getOptionValues("clearRepos"))
                .flatMap(ls -> ls.stream().findFirst().map(Boolean::parseBoolean)).orElse(null);

        if (jsonFile == null) {
            throw new IllegalArgumentException("Please provide a file using --import.file option");
        }

        if (emptyRepos == null) {
            throw new IllegalArgumentException(
                    "Please provide if existing repos should be cleared before importing backup using --clearRepos option");
        }

        File f = new File(jsonFile);
        JSONObject data;
        try (FileInputStream fis = new FileInputStream(f)) {
            data = new JSONObject(StreamUtils.copyToString(fis, StandardCharsets.UTF_8));
        }

        if (emptyRepos) {
            emptyRepos();
        }

        System.out.println();
        System.out.println("========================= Starting Backup Import ========================");
        System.out.println();

        Iterator<String> envIds = data.keys();

        while (envIds.hasNext()) {

            String envId = envIds.next();
            KafkaCluster env = kafkaClusters.getEnvironment(envId).orElse(null);
            if (env == null) {
                System.err.println("Skipping nonexisting environment " + envId + "...");
                continue;
            }

            System.out.println("Importing environment " + envId + "...");
            importBackup(env, data.getJSONObject(envId));
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void importBackup(KafkaCluster env, JSONObject data)
            throws IOException, ExecutionException, InterruptedException {
        Iterator<String> topics = data.keys();

        while (topics.hasNext()) {
            String topic = topics.next();

            System.out.println("Currently importing: " + topic);

            TopicBasedRepository repo = env.getRepositories().stream().filter(r -> topic.equals(r.getTopicName()))
                    .findFirst().orElse(null);

            if (repo == null) {
                System.err.println("Skipping nonexisting repo " + topic + "...");
                continue;
            }

            Class<?> repoClass = repo.getValueClass();

            JSONObject contents = data.getJSONObject(topic);

            Iterator<String> keys = contents.keys();
            while (keys.hasNext()) {
                String key = keys.next();

                JSONObject content = contents.getJSONObject(key);

                HasKey o = (HasKey) objectMapper.readValue(content.toString(), repoClass);
                repo.save(o).get();
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void emptyRepos() {
        System.out.println();
        System.out.println("====================== Clearing Repos before Starting Backup ======================");
        System.out.println();

        kafkaClusters.getEnvironmentIds().forEach(envId -> {
            KafkaCluster cluster = kafkaClusters.getEnvironment(envId).get();
            for (TopicBasedRepository topicBasedRepository : cluster.getRepositories()) {
                for (Object object : topicBasedRepository.getObjects()) {
                    try {
                        topicBasedRepository.delete((HasKey) object).get();
                    }
                    catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
}
