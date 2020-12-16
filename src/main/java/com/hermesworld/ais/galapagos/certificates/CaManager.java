package com.hermesworld.ais.galapagos.certificates;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;

public interface CaManager {

	X509Certificate getCaCertificate();

	CompletableFuture<CertificateSignResult> createApplicationCertificateFromCsr(String applicationId, String csrData,
		String applicationName);

	CompletableFuture<CertificateSignResult> createApplicationCertificateAndPrivateKey(String applicationId,
		String applicationName);

	/**
	 * "Extends" an application certificate. This issues a new certificate with a predefined (passed in) DN and based
	 * on a CSR which must have been created based on the existing private key on the client's end.
	 *
	 * @param dn      Previously used DN, which is now re-used.
	 * @param csrData CSR data received from client.
	 * @return A Future which, once completed, provides the result of the certificate signing process. The future
	 * completes exceptionally if the new certificate could not be issued.
	 */
	CompletableFuture<CertificateSignResult> extendApplicationCertificate(String dn, String csrData);

	boolean supportsDeveloperCertificates();

	CompletableFuture<CertificateSignResult> createDeveloperCertificateAndPrivateKey(String userName);

	CompletableFuture<CertificateSignResult> createToolingCertificateAndPrivateKey();

	File getClientPkcs12File();

	String getClientPkcs12Password();

}
