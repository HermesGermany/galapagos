package com.hermesworld.ais.galapagos.kafka.controller;

import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class EnvironmentsController {

    private final KafkaClusters kafkaEnvironments;

    @Autowired
    public EnvironmentsController(KafkaClusters kafkaEnvironments) {
        this.kafkaEnvironments = kafkaEnvironments;
    }

    @GetMapping(value = "/api/environments", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<KafkaEnvironmentDto> getEnvironments() {
        return kafkaEnvironments.getEnvironmentsMetadata().stream().map(e -> toDto(e)).collect(Collectors.toList());
    }

    @GetMapping(value = "/api/environments/{environmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<KafkaEnvironmentLivenessDto> getEnvironmentLiveness(@PathVariable String environmentId) {
        // TODO currently unsupported
        return Collections.emptyList();
    }

    @GetMapping(value = "/api/environments/{environmentId}/kafkaversion", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getKafkaVersions(@PathVariable String environmentId) throws Exception {
        return kafkaEnvironments.getEnvironment(environmentId).get().getKafkaServerVersion().get();
    }

    private KafkaEnvironmentDto toDto(KafkaEnvironmentConfig env) {
        boolean production = env.getId().equals(kafkaEnvironments.getProductionEnvironmentId());
        return new KafkaEnvironmentDto(env.getId(), env.getName(), env.getBootstrapServers(), production,
                env.isStagingOnly(), env.getAuthenticationMode());
    }

}
