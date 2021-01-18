package com.hermesworld.ais.galapagos.changes.impl;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@Getter
@Setter
final class TopicCreateParamsDto {

    private int numberOfPartitions;

    private int replicationFactor;

    private Map<String, Object> topicConfigs;

    public TopicCreateParamsDto() {
    }

    public TopicCreateParamsDto(TopicCreateParams params) {
        this.numberOfPartitions = params.getNumberOfPartitions();
        this.replicationFactor = params.getReplicationFactor();
        this.topicConfigs = params.getTopicConfigs().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
    }

    public Map<String, String> topicConfigsAsStringMap() {
        return topicConfigs == null ? Collections.emptyMap()
                : topicConfigs.entrySet().stream().collect(
                        Collectors.toMap(e -> e.getKey(), e -> e.getValue() == null ? null : e.getValue().toString()));
    }

    public TopicCreateParams toTopicCreateParams() {
        return new TopicCreateParams(numberOfPartitions, replicationFactor, topicConfigsAsStringMap());
    }

}