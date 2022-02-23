package com.hermesworld.ais.galapagos.devauth.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@Getter
@Setter
public class DeveloperApiKeyDto {

    private final String apiKey;

    private final String secret;

    public DeveloperApiKeyDto(String apiKey, String secret) {
        this.apiKey = apiKey;
        this.secret = secret;
    }

}
