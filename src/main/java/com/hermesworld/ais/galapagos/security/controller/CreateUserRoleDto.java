package com.hermesworld.ais.galapagos.security.controller;

import com.hermesworld.ais.galapagos.security.roles.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRoleDto {

    private String userName;

    private Role role;

    private String applicationId;

    private String comments;
}
