package com.hermesworld.ais.galapagos.certificates.impl;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
final class CaData {

    private X509Certificate caCertificate;

    private PrivateKey caPrivateKey;

    private long applicationCertificateValidity;

    private long developerCertificateValidity;

}
