package com.hermesworld.ais.galapagos.certificates.impl;

import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationConfig;
import com.hermesworld.ais.galapagos.util.CertificateUtil;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509ExtensionUtils;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.openssl.MiscPEMGenerator;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS12PfxPduBuilder;
import org.bouncycastle.pkcs.PKCS12SafeBag;
import org.bouncycastle.pkcs.PKCSException;
import org.bouncycastle.pkcs.bc.BcPKCS12MacCalculatorBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS12SafeBagBuilder;
import org.bouncycastle.util.io.pem.PemWriter;
import org.springframework.util.StringUtils;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages the CA for a single Kafka Cluster.
 *
 * @author AlbrechtFlo
 */
@Slf4j
public final class CaManagerImpl {

    private final CaData caData;

    private final String environmentId;

    private File p12File;

    private String p12Password;

    private static final long DEFAULT_CERTIFICATE_VALIDITY = Duration.ofDays(365).toMillis();

    private static final long TOOLING_VALIDITY = Duration.ofDays(3650).toMillis();

    private static final int PKCS12_PASSWORD_LENGTH = 8;

    private static final ASN1ObjectIdentifier TRUSTED_KEY_USAGE = new ASN1ObjectIdentifier(
            "2.16.840.1.113894.746875.1.1");

    private static final ASN1ObjectIdentifier ANY_EXTENDED_KEY_USAGE = new ASN1ObjectIdentifier("2.5.29.37.0");

    private static final Random RANDOM = new Random();

    public CaManagerImpl(String environmentId, CertificatesAuthenticationConfig config)
            throws IOException, GeneralSecurityException, OperatorCreationException {
        this.environmentId = environmentId;
        this.caData = buildCaData(environmentId, config);
        generateGalapagosPkcs12ClientCertificate(environmentId, config.getClientDn(),
                new File(config.getCertificatesWorkdir()));
    }

    public CompletableFuture<CertificateSignResult> createApplicationCertificateFromCsr(String applicationId,
            String csrData, String applicationName) {
        try (PEMParser parser = new PEMParser(new StringReader(csrData))) {
            Object o = parser.readObject();
            if (!(o instanceof PKCS10CertificationRequest)) {
                return CompletableFuture
                        .failedFuture(new CertificateException("Invalid CSR data: no request found in data"));
            }

            return createCertificate((PKCS10CertificationRequest) o, CertificateUtil.toAppCn(applicationName),
                    caData.getApplicationCertificateValidity());
        }
        catch (IOException e) {
            return CompletableFuture.failedFuture(new CertificateException("Invalid CSR data", e));
        }
    }

    public CompletableFuture<CertificateSignResult> createApplicationCertificateAndPrivateKey(String applicationId,
            String applicationName) {
        return createCertificateAndPrivateKey(applicationName, caData.getApplicationCertificateValidity());
    }

    public CompletableFuture<CertificateSignResult> createToolingCertificateAndPrivateKey() {
        return createCertificateAndPrivateKey("galapagos_tooling", TOOLING_VALIDITY);
    }

    public CompletableFuture<CertificateSignResult> extendApplicationCertificate(String dn, String csrData) {
        X500Name parsedDn = parseAndSortDn(dn);

        try (PEMParser parser = new PEMParser(new StringReader(csrData))) {
            Object o = parser.readObject();
            if (!(o instanceof PKCS10CertificationRequest)) {
                return CompletableFuture
                        .failedFuture(new CertificateException("Invalid CSR data: no request found in data"));
            }

            PKCS10CertificationRequest csr = (PKCS10CertificationRequest) o;
            if (!parsedDn.equals(csr.getSubject())) {
                return CompletableFuture.failedFuture(
                        new CertificateParsingException("The CSR is not valid for extending this certificate"));
            }

            return createCertificate(csr, parsedDn, caData.getApplicationCertificateValidity());
        }
        catch (IOException e) {
            return CompletableFuture.failedFuture(new CertificateException("Invalid CSR data", e));
        }
    }

    public boolean supportsDeveloperCertificates() {
        return caData.getDeveloperCertificateValidity() > 0;
    }

    public CompletableFuture<CertificateSignResult> createDeveloperCertificateAndPrivateKey(String userName) {
        if (!supportsDeveloperCertificates()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Developer certificates are not enabled for this environment."));
        }
        return createCertificateAndPrivateKey(userName, caData.getDeveloperCertificateValidity());
    }

    public byte[] buildTrustStore() throws IOException, PKCSException {
        PKCS12PfxPduBuilder keyStoreBuilder = new PKCS12PfxPduBuilder();

        keyStoreBuilder.addData(new JcaPKCS12SafeBagBuilder(getCaCertificate())
                .addBagAttribute(PKCS12SafeBag.friendlyNameAttribute,
                        new DERBMPString("kafka_" + environmentId + "_ca"))
                .addBagAttribute(TRUSTED_KEY_USAGE, ANY_EXTENDED_KEY_USAGE).build());

        return keyStoreBuilder.build(new BcPKCS12MacCalculatorBuilder(), "changeit".toCharArray())
                .getEncoded(ASN1Encoding.DL);
    }

    public File getClientPkcs12File() {
        return p12File;
    }

    public String getClientPkcs12Password() {
        return p12Password;
    }

    private CompletableFuture<CertificateSignResult> createCertificateAndPrivateKey(String userOrAppName,
            long validityMs) {
        String cn = CertificateUtil.toAppCn(userOrAppName);

        try {
            KeyPair pair = CertificateUtil.generateKeyPair();

            X500Name name = CertificateUtil.uniqueX500Name(cn);
            PKCS10CertificationRequest csr = CertificateUtil.buildCsr(name, pair);

            return createCertificate(csr, cn, validityMs).thenCompose(result -> {
                try {
                    return CompletableFuture.completedFuture(new CertificateSignResult(result.getCertificate(),
                            result.getCertificatePemData(), result.getDn(), CertificateUtil.buildPrivateKeyStore(
                                    result.getCertificate(), pair.getPrivate(), "changeit".toCharArray())));
                }
                catch (OperatorCreationException | PKCSException e) {
                    return CompletableFuture.failedFuture(
                            new CertificateException("Could not generate internal CSR for certificate", e));
                }
                catch (GeneralSecurityException e) {
                    return CompletableFuture.failedFuture(
                            new RuntimeException("Java Security is configured wrong, or Bouncycastle not found"));
                }
                catch (IOException e) {
                    // should not occur in-memory
                    return CompletableFuture
                            .failedFuture(new RuntimeException("Exception when writing into memory", e));
                }
            });
        }
        catch (GeneralSecurityException e) {
            return CompletableFuture
                    .failedFuture(new RuntimeException("Java Security is configured wrong, or Bouncycastle not found"));
        }
        catch (OperatorCreationException e) {
            return CompletableFuture
                    .failedFuture(new CertificateException("Could not generate internal CSR for certificate", e));
        }
    }

    private void generateGalapagosPkcs12ClientCertificate(String envId, String dn, File certificateWorkdir)
            throws GeneralSecurityException, OperatorCreationException {
        KeyPair pair = CertificateUtil.generateKeyPair();
        X500Name name = new X500Name(dn);

        PKCS10CertificationRequest csr = CertificateUtil.buildCsr(name, pair);
        X509CertificateHolder holder = signCertificateRequest(csr, name,
                ChronoUnit.YEARS.getDuration().toMillis() * 10);

        X509Certificate publicCert = new JcaX509CertificateConverter().getCertificate(holder);

        try {
            String password = generatePkcs12Password();
            byte[] data = CertificateUtil.buildPrivateKeyStore(publicCert, pair.getPrivate(), password.toCharArray());
            if (!certificateWorkdir.isDirectory() && !certificateWorkdir.mkdirs()) {
                throw new IOException(
                        "Could not create certificate working directory " + certificateWorkdir.getAbsolutePath());
            }
            File fCertificate = new File(certificateWorkdir, "galapagos_" + envId + "_client.p12");
            try (FileOutputStream fos = new FileOutputStream(fCertificate)) {
                fos.write(data);
            }
            this.p12File = fCertificate;
            this.p12Password = password;
        }
        catch (IOException | PKCSException e) {
            throw new GeneralSecurityException(e);
        }
    }

    private CompletableFuture<CertificateSignResult> createCertificate(PKCS10CertificationRequest csr,
            String commonName, long validityMs) {
        String cn = CertificateUtil.extractCn(csr.getSubject());

        // MUST match application name
        if (cn == null) {
            return CompletableFuture.failedFuture(
                    new CertificateParsingException("No CN attribute present in Certificate Request. Please include CN="
                            + commonName + " as subject in the request."));
        }
        if (!cn.equals(commonName)) {
            return CompletableFuture
                    .failedFuture(new CertificateParsingException("Wrong CN in Certificate Request. Please include CN="
                            + commonName + " as subject in the request."));
        }

        return createCertificate(csr, CertificateUtil.uniqueX500Name(commonName), validityMs);
    }

    private CompletableFuture<CertificateSignResult> createCertificate(PKCS10CertificationRequest csr, X500Name dn,
            long validityMs) {
        StringBuilder pemData = new StringBuilder();

        X509Certificate certificate;
        try {
            certificate = createSignedPemCertificate(csr, dn, pemData, validityMs);
        }
        catch (CertificateException e) {
            return CompletableFuture.failedFuture(e);
        }

        String newDn = certificate.getSubjectX500Principal().toString().replace(", OU=", ",OU=");
        return CompletableFuture
                .completedFuture(new CertificateSignResult(certificate, pemData.toString(), newDn, null));
    }

    private X509Certificate createSignedPemCertificate(PKCS10CertificationRequest csr, X500Name dn,
            StringBuilder pemDataHolder, long validityMs) throws CertificateException {
        try {
            X509CertificateHolder holder = signCertificateRequest(csr, dn, validityMs);

            StringWriter sw = new StringWriter();
            try (PemWriter writer = new PemWriter(sw)) {
                writer.writeObject(new MiscPEMGenerator(holder));
            }
            pemDataHolder.append(sw);

            return new JcaX509CertificateConverter().getCertificate(holder);
        }
        catch (IOException e) {
            throw new CertificateException("Exception during reading certificate data", e);
        }
        catch (OperatorCreationException e) {
            throw new CertificateException("Could not sign certificate", e);
        }
        catch (CertificateParsingException e) {
            throw e;
        }
        catch (GeneralSecurityException e) {
            // intentional RuntimeException as this should fall through
            throw new RuntimeException("Missing algorithm or other configuration problem for certificate signing", e);
        }
    }

    private X509CertificateHolder signCertificateRequest(PKCS10CertificationRequest csr, X500Name subject,
            long validityMs) throws GeneralSecurityException, OperatorCreationException {
        X500Name caName = new JcaX509CertificateHolder(caData.getCaCertificate()).getSubject();

        X509v3CertificateBuilder certGenerator = new X509v3CertificateBuilder(caName, getSerial(subject),
                new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)),
                new Date(System.currentTimeMillis() + validityMs), subject, csr.getSubjectPublicKeyInfo());

        try {
            certGenerator.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));

            X509ExtensionUtils utils = new JcaX509ExtensionUtils();
            certGenerator.addExtension(Extension.subjectKeyIdentifier, false,
                    utils.createSubjectKeyIdentifier(csr.getSubjectPublicKeyInfo()));

            X509CertificateHolder caHolder = new X509CertificateHolder(caData.getCaCertificate().getEncoded());
            certGenerator.addExtension(Extension.authorityKeyIdentifier, false,
                    utils.createAuthorityKeyIdentifier(caHolder));

            AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withRSA");
            AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
            ContentSigner signer = new BcRSAContentSignerBuilder(sigAlgId, digAlgId)
                    .build(PrivateKeyFactory.createKey(caData.getCaPrivateKey().getEncoded()));
            return certGenerator.build(signer);
        }
        catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
    }

    private BigInteger getSerial(X500Name name) {
        BigInteger millis = BigInteger.valueOf(System.currentTimeMillis());
        millis = millis.multiply(BigInteger.valueOf(Integer.MAX_VALUE));
        return millis.add(BigInteger.valueOf(name.toString().hashCode()));
    }

    private X509Certificate getCaCertificate() {
        return caData.getCaCertificate();
    }

    private static CaData buildCaData(String environmentId, CertificatesAuthenticationConfig config)
            throws IOException, GeneralSecurityException {
        CaData data = new CaData();

        if (config.getCaCertificateFile() == null) {
            throw new RuntimeException(
                    "Missing configuration property caCertificateFile for environment " + environmentId);
        }
        if (config.getCaKeyFile() == null) {
            throw new RuntimeException("Missing configuration property caKeyFile for environment " + environmentId);
        }

        try (InputStream inPublic = config.getCaCertificateFile().getInputStream();
                PEMParser keyParser = new PEMParser(
                        new InputStreamReader(config.getCaKeyFile().getInputStream(), StandardCharsets.ISO_8859_1))) {
            data.setCaCertificate(
                    (X509Certificate) CertificateFactory.getInstance("X.509", "BC").generateCertificate(inPublic));

            Object object = keyParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

            PEMKeyPair ukp = (PEMKeyPair) object;
            KeyPair kp = converter.getKeyPair(ukp);

            if (!Arrays.equals(kp.getPublic().getEncoded(), data.getCaCertificate().getPublicKey().getEncoded())) {
                throw new RuntimeException("The public/private key pair does not match for certificate "
                        + data.getCaCertificate().getSubjectX500Principal().getName());
            }

            data.setCaPrivateKey(kp.getPrivate());
        }

        try {
            data.setApplicationCertificateValidity(
                    !StringUtils.hasLength(config.getApplicationCertificateValidity()) ? DEFAULT_CERTIFICATE_VALIDITY
                            : Duration.parse(config.getApplicationCertificateValidity()).toMillis());
        }
        catch (DateTimeParseException e) {
            log.warn("Invalid duration pattern found in application configuration: "
                    + config.getApplicationCertificateValidity());
            data.setApplicationCertificateValidity(DEFAULT_CERTIFICATE_VALIDITY);
        }
        try {
            data.setDeveloperCertificateValidity(!StringUtils.hasLength(config.getDeveloperCertificateValidity()) ? 0
                    : Duration.parse(config.getDeveloperCertificateValidity()).toMillis());
        }
        catch (DateTimeParseException e) {
            log.warn("Invalid duration pattern found in application configuration: "
                    + config.getApplicationCertificateValidity());
            data.setApplicationCertificateValidity(DEFAULT_CERTIFICATE_VALIDITY);
        }

        return data;
    }

    private static String generatePkcs12Password() {
        String allowedChars = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sbPass = new StringBuilder();

        for (int i = 0; i < PKCS12_PASSWORD_LENGTH; i++) {
            sbPass.append(allowedChars.charAt(RANDOM.nextInt(allowedChars.length())));
        }
        return sbPass.toString();
    }

    private static X500Name parseAndSortDn(String rawDn) {
        X500Name name = new X500Name(rawDn);
        if (name.getRDNs(X509ObjectIdentifiers.organizationalUnitName).length == 0) {
            return name;
        }

        X500NameBuilder builder = new X500NameBuilder();
        builder.addRDN(name.getRDNs(X509ObjectIdentifiers.organizationalUnitName)[0].getFirst());
        builder.addRDN(name.getRDNs(X509ObjectIdentifiers.commonName)[0].getFirst());
        return builder.build();
    }
}
