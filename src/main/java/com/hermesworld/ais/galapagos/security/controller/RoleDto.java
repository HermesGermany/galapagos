package com.hermesworld.ais.galapagos.security.controller;

import com.hermesworld.ais.galapagos.security.roles.Role;
import lombok.Getter;

@Getter
public class RoleDto {
    private final String id;

    private final String userName;

    private final Role role;

    private final String applicationId;

    public RoleDto(String id, String userName, Role role, String applicationId) {
        this.id = id;
        this.userName = userName;
        this.role = role;
        this.applicationId = applicationId;
    }
}
