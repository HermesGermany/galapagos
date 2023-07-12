package com.hermesworld.ais.galapagos.security;

import lombok.Getter;

@Getter
public final class AuditPrincipal {

    private String name;

    private String fullName;

    public AuditPrincipal(String name, String fullName) {
        this.name = name;
        this.fullName = fullName;
    }

    public String getFullName() {
        return fullName;
    }
}
