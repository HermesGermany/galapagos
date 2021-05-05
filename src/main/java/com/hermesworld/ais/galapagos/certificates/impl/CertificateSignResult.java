package com.hermesworld.ais.galapagos.certificates.impl;

import lombok.Getter;

import java.security.cert.X509Certificate;
import java.util.Optional;

@Getter
public final class CertificateSignResult {

    private final X509Certificate certificate;

    private final String certificatePemData;

    private final Optional<byte[]> p12Data;

    private final String dn;

    public CertificateSignResult(X509Certificate certificate, String certificatePemData, String dn, byte[] p12Data) {
        this.certificate = certificate;
        this.certificatePemData = certificatePemData;
        this.dn = dn;
        this.p12Data = Optional.ofNullable(p12Data);
    }

}
