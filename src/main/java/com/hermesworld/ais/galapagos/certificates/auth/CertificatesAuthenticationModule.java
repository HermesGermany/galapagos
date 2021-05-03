package com.hermesworld.ais.galapagos.certificates.auth;

import com.hermesworld.ais.galapagos.kafka.auth.CreateAuthenticationResult;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class CertificatesAuthenticationModule implements KafkaAuthenticationModule {

    private final CertificatesAuthenticationConfig config;

    public CertificatesAuthenticationModule(CertificatesAuthenticationConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<Void> init() {
        // TODO
        throw new UnsupportedOperationException("Certificates authentication currently not supported");
    }

    @Override
    public CompletableFuture<CreateAuthenticationResult> createApplicationAuthentication(String applicationId,
            String applicationNormalizedName, JSONObject createParameters) {
        // TODO
        throw new UnsupportedOperationException("Certificates authentication currently not supported");
    }

    @Override
    public CompletableFuture<Void> deleteApplicationAuthentication(String applicationId, JSONObject existingAuthData) {
        // TODO
        throw new UnsupportedOperationException("Certificates authentication currently not supported");
    }

    @Override
    public void addRequiredKafkaProperties(Properties kafkaProperties) {
        // TODO
        throw new UnsupportedOperationException("Certificates authentication currently not supported");
    }

    @Override
    public String extractKafkaUserName(String applicationId, JSONObject existingAuthData) throws JSONException {
        // TODO
        throw new UnsupportedOperationException("Certificates authentication currently not supported");
    }
}
