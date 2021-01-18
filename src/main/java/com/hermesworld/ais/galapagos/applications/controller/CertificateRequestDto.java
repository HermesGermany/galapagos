package com.hermesworld.ais.galapagos.applications.controller;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CertificateRequestDto {

    private String csrData;

    private boolean generateKey;

    private String topicPrefix;

    private boolean extendCertificate;

}
