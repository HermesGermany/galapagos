package com.hermesworld.ais.galapagos.applications.controller;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;

@JsonDeserialize
@Getter
@Setter
public class CertificateRequestDto {

    private String csrData;

    private boolean generateKey;

    private boolean extendCertificate;

}
