package com.hermesworld.ais.galapagos.devauth.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.applications.controller.AuthenticationDto;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@JsonSerialize
@Getter
@Setter
public class DeveloperAuthenticationsDto {

    private Map<String, AuthenticationDto> authentications;

}
