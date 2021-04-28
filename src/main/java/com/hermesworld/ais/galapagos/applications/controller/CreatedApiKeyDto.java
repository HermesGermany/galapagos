package com.hermesworld.ais.galapagos.applications.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@Getter
@Setter
public class CreatedApiKeyDto {

    private String apiKey;

    private String apiSecret;

}
