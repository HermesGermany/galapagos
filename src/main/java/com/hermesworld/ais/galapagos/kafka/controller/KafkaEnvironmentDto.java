package com.hermesworld.ais.galapagos.kafka.controller;

import lombok.Getter;

@Getter
public class KafkaEnvironmentDto {

    private final String id;

    private final String name;

    private final String bootstrapServers;

    private final boolean production;

    private final boolean stagingOnly;

    private final String authenticationMode;

    public KafkaEnvironmentDto(String id, String name, String bootstrapServers, boolean production,
            boolean stagingOnly, String authenticationMode) {
        this.id = id;
        this.name = name;
        this.bootstrapServers = bootstrapServers;
        this.production = production;
        this.stagingOnly = stagingOnly;
        this.authenticationMode = authenticationMode;
    }

}
