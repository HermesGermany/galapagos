package com.hermesworld.ais.galapagos.devauth.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;

import java.time.Instant;

@JsonSerialize
@Getter
public class DeveloperAuthenticationInfoDto {

    private final String dn;

    @JsonFormat(shape = Shape.STRING)
    private final Instant expiresAt;

    public DeveloperAuthenticationInfoDto(String dn, Instant expiresAt) {
        this.dn = dn;
        this.expiresAt = expiresAt;
    }

}
