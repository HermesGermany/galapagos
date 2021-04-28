package com.hermesworld.ais.galapagos.certificates.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.core.io.Resource;

@Getter
@Setter
public class CertificatesAuthenticationConfig {

    private Resource caCertificateFile;

    private Resource caKeyFile;

    private String applicationCertificateValidity;

    private String developerCertificateValidity;

    private String clientDn;

}
