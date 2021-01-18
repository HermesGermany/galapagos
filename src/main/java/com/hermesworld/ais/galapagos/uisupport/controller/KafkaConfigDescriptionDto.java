package com.hermesworld.ais.galapagos.uisupport.controller;

import lombok.Getter;

@Getter
public class KafkaConfigDescriptionDto {

    private String configName;

    private String configDescription;

    public KafkaConfigDescriptionDto(String configName, String configDescription) {
        this.configName = configName;
        this.configDescription = configDescription;
    }

}
