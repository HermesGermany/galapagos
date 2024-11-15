package com.hermesworld.ais.galapagos.security.roles;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;

public class ApplicationGrantedAuthority implements GrantedAuthority {
    private final String role;
    @Getter
    private final String applicationId;

    public ApplicationGrantedAuthority(String role, String applicationId) {
        this.role = role;
        this.applicationId = applicationId;
    }

    @Override
    public String getAuthority() {
        return role;
    }

}

