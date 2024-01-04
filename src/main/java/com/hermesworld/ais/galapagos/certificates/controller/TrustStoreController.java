package com.hermesworld.ais.galapagos.certificates.controller;

import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.config.SslConfigs;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.function.Supplier;

@RestController
@Slf4j
public class TrustStoreController {

    private final KafkaClusters kafkaClusters;

    private final Supplier<ResponseStatusException> notFound = () -> new ResponseStatusException(HttpStatus.NOT_FOUND);

    public TrustStoreController(KafkaClusters kafkaClusters) {
        this.kafkaClusters = kafkaClusters;
    }

    // intentionally not protected - no /api path
    @GetMapping(value = "/files/truststore/{environmentId}", produces = "application/x-pkcs12")
    public ResponseEntity<byte[]> getTrustStore(@PathVariable String environmentId) {
        String authMode = kafkaClusters.getEnvironmentMetadata(environmentId).map(meta -> meta.getAuthenticationMode())
                .orElseThrow(notFound);
        if (!"certificates".equals(authMode)) {
            throw notFound.get();
        }

        // trick to extract trust store - use Kafka properties!
        KafkaAuthenticationModule module = kafkaClusters.getAuthenticationModule(environmentId).orElseThrow(notFound);
        Properties props = new Properties();
        module.addRequiredKafkaProperties(props);

        String trustStoreLocation = props.getProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG);
        if (ObjectUtils.isEmpty(trustStoreLocation)) {
            throw notFound.get();
        }

        try (FileInputStream fis = new FileInputStream(trustStoreLocation)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            StreamUtils.copy(fis, baos);
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=kafka-truststore-" + environmentId + ".jks");
            return new ResponseEntity<>(baos.toByteArray(), responseHeaders, HttpStatus.OK);
        }
        catch (IOException e) {
            log.error("Could not read truststore " + trustStoreLocation, e);
            throw notFound.get();
        }
    }

}
