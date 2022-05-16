package com.hermesworld.ais.galapagos.devauth.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@JsonSerialize
@Getter
@Setter
public class DeveloperApiKeyDto {

    private final String apiKey;

    private final String secret;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Instant expiresAt;

    public DeveloperApiKeyDto(String apiKey, String secret, Instant expiresAt) {
        this.apiKey = apiKey;
        this.secret = secret;
        this.expiresAt = expiresAt;
    }

}
