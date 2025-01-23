package com.hermesworld.ais.galapagos.security.roles;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@Getter
@Setter
public class UserRoleData implements HasKey {

    private String id;

    private String userName;

    private Role role;

    private String applicationId;

    public UserRoleData() {
    }

    public UserRoleData(UserRoleData original) {
        this.id = original.id;
        this.userName = original.userName;
        this.role = original.role;
        this.applicationId = original.applicationId;
    }

    @Override
    public String key() {
        return id;
    }

    @Override
    public String toString() {
        return "UserRoleData{" + "id='" + id + '\'' + ", userName='" + userName + '\'' + ", role=" + role
                + ", applicationId='" + applicationId + '\'' + '}' + '\n';
    }
}
