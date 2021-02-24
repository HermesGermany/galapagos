package com.hermesworld.ais.galapagos.kafka.controller;

import lombok.Getter;

@Getter
public class KafkaEnvironmentDto {

    private String id;

    private String name;

    private String bootstrapServers;

    private boolean production;

    private boolean stagingOnly;

    public KafkaEnvironmentDto(String id, String name, String bootstrapServers, boolean production,
            boolean stagingOnly) {
        this.id = id;
        this.name = name;
        this.bootstrapServers = bootstrapServers;
        this.production = production;
        this.stagingOnly = stagingOnly;
    }

}
