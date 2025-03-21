package com.hermesworld.ais.galapagos.security.controller;

import com.hermesworld.ais.galapagos.security.roles.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleRequestSubmissionDto {

    private String applicationId;

    private Role role;

    private String environmentId;

    private String comments;

}
