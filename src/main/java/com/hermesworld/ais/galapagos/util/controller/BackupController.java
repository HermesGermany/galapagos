package com.hermesworld.ais.galapagos.util.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.util.HasKey;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Slf4j
public class BackupController {

    private final KafkaClusters kafkaClusters;

    private final ObjectMapper objectMapper = JsonUtil.newObjectMapper();

    public BackupController(KafkaClusters kafkaClusters) {
        this.kafkaClusters = kafkaClusters;
    }

    @GetMapping(value = "/api/admin/full-backup", produces = MediaType.APPLICATION_JSON_VALUE)
    @Secured("ROLE_ADMIN")
    public String createBackup() {
        JSONObject result = new JSONObject();

        for (String id : kafkaClusters.getEnvironmentIds()) {
            kafkaClusters.getEnvironment(id).ifPresent(env -> result.put(id, backupEnvironment(env)));
        }

        return result.toString();
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
        for (HasKey obj : repo.getObjects()) {
            try {
                result.put(obj.key(), new JSONObject(objectMapper.writeValueAsString(obj)));
            }
            catch (JSONException | JsonProcessingException e) {
                log.error("Could not serialize object for backup", e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        }
        return result;
    }
}
