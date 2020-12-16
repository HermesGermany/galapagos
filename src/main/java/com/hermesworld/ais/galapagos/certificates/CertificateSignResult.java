package com.hermesworld.ais.galapagos.certificates;

import java.security.cert.X509Certificate;
import java.util.Optional;

import lombok.Getter;

@Getter
public final class CertificateSignResult {

	private X509Certificate certificate;

	private String certificatePemData;

	private Optional<byte[]> p12Data;

	private String dn;

	public CertificateSignResult(X509Certificate certificate, String certificatePemData, String dn, byte[] p12Data) {
		this.certificate = certificate;
		this.certificatePemData = certificatePemData;
		this.dn = dn;
		this.p12Data = Optional.ofNullable(p12Data);
	}

}
