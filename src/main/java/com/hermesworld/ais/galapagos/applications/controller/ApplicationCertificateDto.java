package com.hermesworld.ais.galapagos.applications.controller;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplicationCertificateDto {

    private String environmentId;

    private String dn;

    private String expiresAt;

    public ApplicationCertificateDto(String environmentId, String dn, String expiresAt) {
        this.environmentId = environmentId;
        this.dn = dn;
        this.expiresAt = expiresAt;
    }

}
