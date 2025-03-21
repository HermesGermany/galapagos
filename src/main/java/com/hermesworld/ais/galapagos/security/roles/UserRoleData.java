package com.hermesworld.ais.galapagos.security.roles;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@JsonSerialize
@Getter
@Setter
public class UserRoleData implements HasKey {

    private String id;

    private String userName;

    private Role role;

    private String environmentId;

    private String applicationId;

    private String comments;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private ZonedDateTime createdAt;

    private String notificationEmailAddress;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private ZonedDateTime lastStatusChangeAt;

    private String lastStatusChangeBy;

    private RequestState state;

    @Override
    public String key() {
        return id;
    }

    @Override
    public String toString() {
        return "UserRoleData{" + "id='" + id + '\'' + ", userName='" + userName + '\'' + ", role=" + role + '\''
                + ", applicationId='" + applicationId + '\'' + ", environmentId='" + environmentId + '\''
                + ", comments='" + comments + '\'' + ", createdAt=" + createdAt + ", lastStatusChangeAt="
                + lastStatusChangeAt + ", lastStatusChangeBy='" + lastStatusChangeBy + '\'' + ", state=" + state + '\''
                + '}' + '\n';
    }
}
