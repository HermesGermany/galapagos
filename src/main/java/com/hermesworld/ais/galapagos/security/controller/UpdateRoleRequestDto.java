package com.hermesworld.ais.galapagos.security.controller;

import com.hermesworld.ais.galapagos.applications.RequestState;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateRoleRequestDto {
    private RequestState newState;
}
