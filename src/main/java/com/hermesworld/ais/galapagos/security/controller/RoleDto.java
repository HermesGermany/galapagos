package com.hermesworld.ais.galapagos.security.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.security.roles.Role;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
public class RoleDto {
    private final String id;

    private final String userName;

    private final Role role;

    private final String environmentId;

    private final String applicationId;

    private final String comments;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final ZonedDateTime createdAt;

    private final String notificationEmailAddress;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final ZonedDateTime lastStatusChangeAt;

    private final String lastStatusChangeBy;

    private final RequestState state;

    public RoleDto(String id, String userName, Role role, String environment, String applicationId1, String comments,
            ZonedDateTime createdAt, String notificationEmailAddress, ZonedDateTime lastStatusChangeAt,
            String lastStatusChangeBy, RequestState state) {
        this.id = id;
        this.userName = userName;
        this.role = role;
        this.environmentId = environment;
        this.applicationId = applicationId1;
        this.comments = comments;
        this.createdAt = createdAt;
        this.notificationEmailAddress = notificationEmailAddress;
        this.lastStatusChangeAt = lastStatusChangeAt;
        this.lastStatusChangeBy = lastStatusChangeBy;
        this.state = state;
    }
}
