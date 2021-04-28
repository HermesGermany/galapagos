package com.hermesworld.ais.galapagos.applications.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@JsonSerialize
@Getter
@Setter
public class ApplicationAuthenticationsDto {

    private Map<String, AuthenticationDto> authentications;

}
