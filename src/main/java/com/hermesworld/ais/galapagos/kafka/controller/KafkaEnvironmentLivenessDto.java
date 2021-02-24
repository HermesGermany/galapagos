package com.hermesworld.ais.galapagos.kafka.controller;

import lombok.Getter;

@Getter
public class KafkaEnvironmentLivenessDto {

    private String server;

    private boolean online;

    public KafkaEnvironmentLivenessDto(String server, boolean online) {
        this.server = server;
        this.online = online;
    }

}
