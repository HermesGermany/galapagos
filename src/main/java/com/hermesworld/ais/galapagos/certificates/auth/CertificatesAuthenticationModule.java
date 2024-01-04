package com.hermesworld.ais.galapagos.certificates.auth;

import com.hermesworld.ais.galapagos.certificates.impl.CaManagerImpl;
import com.hermesworld.ais.galapagos.certificates.impl.CertificateSignResult;
import com.hermesworld.ais.galapagos.kafka.auth.CreateAuthenticationResult;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCSException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class CertificatesAuthenticationModule implements KafkaAuthenticationModule {

    private final String environmentId;

    private final CertificatesAuthenticationConfig config;

    private CaManagerImpl caManager;

    private String truststoreFile;

    private String truststorePassword;

    private final static String EXPIRES_AT = "expiresAt";

    private final static String DN = "dn";

    public CertificatesAuthenticationModule(String environmentId, CertificatesAuthenticationConfig config) {
        this.environmentId = environmentId;
        this.config = config;
    }

    @Override
    public CompletableFuture<Void> init() {
        try {
            Files.createDirectories(Path.of(config.getCertificatesWorkdir()));
            this.caManager = new CaManagerImpl(environmentId, config);
            if (StringUtils.isEmpty(config.getTruststoreFile())) {
                createDynamicTruststore();
            }
            else {
                this.truststoreFile = config.getTruststoreFile();
                this.truststorePassword = config.getTruststorePassword();
            }

            return CompletableFuture.completedFuture(null);
        }
        catch (IOException | GeneralSecurityException | OperatorCreationException | PKCSException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<CreateAuthenticationResult> createApplicationAuthentication(String applicationId,
            String applicationNormalizedName, JSONObject createParameters) {
        boolean extendCertificate = createParameters.optBoolean("extendCertificate", false);

        // wrong method called for extending certificate!
        if (extendCertificate) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Cannot extend certificate - application does not yet have certificate on this environment"));
        }

        return createOrUpdateApplicationAuthentication(applicationId, applicationNormalizedName, createParameters,
                null);
    }

    @Override
    public CompletableFuture<CreateAuthenticationResult> updateApplicationAuthentication(String applicationId,
            String applicationNormalizedName, JSONObject createParameters, JSONObject existingAuthData) {
        boolean extendCertificate = createParameters.optBoolean("extendCertificate", false);
        String dn = null;

        if (extendCertificate) {
            dn = existingAuthData.optString(DN);
            if (ObjectUtils.isEmpty(dn)) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Cannot extend certificate - no certificate information available for application"));
            }
        }

        return createOrUpdateApplicationAuthentication(applicationId, applicationNormalizedName, createParameters, dn);
    }

    private CompletableFuture<CreateAuthenticationResult> createOrUpdateApplicationAuthentication(String applicationId,
            String applicationNormalizedName, JSONObject createParameters, String dn) {

        boolean generateKey = createParameters.optBoolean("generateKey", false);
        ByteArrayOutputStream outSecret = new ByteArrayOutputStream();

        if (!generateKey) {
            String csr = createParameters.optString("csrData");
            if (ObjectUtils.isEmpty(csr)) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "No CSR (csrData) present! Set generateKey to true if you want the server to generate a private key for you (not recommended)."));
            }

            return createApplicationCertificateFromCsr(applicationId, applicationNormalizedName, csr, dn, outSecret)
                    .thenApply(result -> new CreateAuthenticationResult(result, outSecret.toByteArray()));
        }
        else {
            if (!this.config.isAllowPrivateKeyGeneration()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Private Key generation not enabled for environment " + environmentId));
            }
            return createApplicationCertificateAndPrivateKey(applicationId, applicationNormalizedName, outSecret)
                    .thenApply(result -> new CreateAuthenticationResult(result, outSecret.toByteArray()));
        }
    }

    @Override
    public void addRequiredKafkaProperties(Properties props) {
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        props.put(CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG, "60000");

        props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreFile);
        props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword);

        props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, caManager.getClientPkcs12File().getAbsolutePath());
        props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, caManager.getClientPkcs12Password());
        props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12");
        props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "");
    }

    @Override
    public String extractKafkaUserName(JSONObject existingAuthData) throws JSONException {
        return "User:" + existingAuthData.getString(DN);
    }

    @Override
    public CompletableFuture<CreateAuthenticationResult> createDeveloperAuthentication(String userName,
            JSONObject createParams) {
        ByteArrayOutputStream outSecret = new ByteArrayOutputStream();
        return createDeveloperCertificateAndPrivateKey(userName, outSecret)
                .thenApply(result -> new CreateAuthenticationResult(result, outSecret.toByteArray()));
    }

    @Override
    public CompletableFuture<Void> deleteApplicationAuthentication(String applicationId, JSONObject existingAuthData) {
        // nothing to do here - enough to remove ACLs (done via update listener)
        return FutureUtil.noop();
    }

    @Override
    public CompletableFuture<Void> deleteDeveloperAuthentication(String userName, JSONObject existingAuthData) {
        return FutureUtil.noop();
    }

    private CompletableFuture<JSONObject> createApplicationCertificateFromCsr(String applicationId,
            String normalizedApplicationName, String csrData, String existingDn, OutputStream outputStreamForCerFile) {
        CompletableFuture<CertificateSignResult> future = existingDn != null
                ? caManager.extendApplicationCertificate(existingDn, csrData)
                : caManager.createApplicationCertificateFromCsr(applicationId, csrData, normalizedApplicationName);

        return future.thenCompose(result -> {
            try {
                outputStreamForCerFile.write(result.getCertificatePemData().getBytes(StandardCharsets.UTF_8));
            }
            catch (IOException e) {
                return CompletableFuture.failedFuture(e);
            }
            return CompletableFuture.completedFuture(result);
        }).thenApply(result -> toAuthJson(result));
    }

    public CompletableFuture<CertificateSignResult> createDeveloperCertificateAndPrivateKey(String userName) {
        return caManager.createDeveloperCertificateAndPrivateKey(userName);
    }

    public boolean supportsDeveloperCertificates() {
        return caManager.supportsDeveloperCertificates();
    }

    private CompletableFuture<JSONObject> createApplicationCertificateAndPrivateKey(String applicationId,
            String applicationNormalizedName, OutputStream outputStreamForP12File) {
        return caManager.createApplicationCertificateAndPrivateKey(applicationId, applicationNormalizedName)
                .thenCompose(result -> {
                    try {
                        outputStreamForP12File.write(result.getP12Data().orElse(new byte[0]));
                    }
                    catch (IOException e) {
                        return CompletableFuture.failedFuture(e);
                    }

                    return CompletableFuture.completedFuture(toAuthJson(result));
                });
    }

    private CompletableFuture<JSONObject> createDeveloperCertificateAndPrivateKey(String userName,
            OutputStream outputStreamForP12File) {
        return caManager.createDeveloperCertificateAndPrivateKey(userName).thenCompose(result -> {
            try {
                outputStreamForP12File.write(result.getP12Data().orElse(new byte[0]));
            }
            catch (IOException e) {
                return CompletableFuture.failedFuture(e);
            }

            return CompletableFuture.completedFuture(toAuthJson(result));
        });
    }

    private void createDynamicTruststore() throws IOException, PKCSException {
        byte[] truststore = caManager.buildTrustStore();
        File outFile = new File(config.getCertificatesWorkdir(), "kafka_" + environmentId + "_truststore.jks");
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(truststore);
        }

        this.truststoreFile = outFile.getAbsolutePath();
        this.truststorePassword = "changeit";
    }

    private JSONObject toAuthJson(CertificateSignResult result) {
        Instant expiresAt = Instant.ofEpochMilli(result.getCertificate().getNotAfter().getTime());
        return new JSONObject(Map.of(DN, result.getDn(), EXPIRES_AT, expiresAt.toString()));
    }

    @Override
    public Optional<Instant> extractExpiryDate(JSONObject authData) {
        if (authData.has(EXPIRES_AT)) {
            return Optional.of(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(authData.getString(EXPIRES_AT))));
        }
        return Optional.empty();
    }

    public static String getDnFromJson(JSONObject authData) {
        return authData.optString(DN);
    }
}
