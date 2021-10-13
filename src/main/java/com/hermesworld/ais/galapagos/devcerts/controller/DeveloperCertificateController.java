package com.hermesworld.ais.galapagos.devcerts.controller;

import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationModule;
import com.hermesworld.ais.galapagos.certificates.impl.CertificateSignResult;
import com.hermesworld.ais.galapagos.devcerts.DevCertificateMetadata;
import com.hermesworld.ais.galapagos.devcerts.DeveloperCertificateService;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

@RestController
@Slf4j
public class DeveloperCertificateController {

    private final DeveloperCertificateService certificateService;

    private final CurrentUserService userService;

    private final KafkaClusters kafkaClusters;

    public DeveloperCertificateController(DeveloperCertificateService certificateService,
            CurrentUserService userService, KafkaClusters kafkaClusters) {
        this.certificateService = certificateService;
        this.userService = userService;
        this.kafkaClusters = kafkaClusters;

    }

    @PostMapping(value = "/api/me/certificates/{environmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeveloperCertificateDto createDeveloperCertificate(@PathVariable String environmentId) {
        KafkaEnvironmentConfig metadata = kafkaClusters.getEnvironmentMetadata(environmentId).orElseThrow();
        if ("ccloud".equals(metadata.getAuthenticationMode())) {
            throw new IllegalStateException(
                    "Environment " + environmentId + " does not use Certificates for authentication.");
        }

        String userName = userService.getCurrentUserName()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        KafkaAuthenticationModule authModule = kafkaClusters.getAuthenticationModule(environmentId)
                .orElseThrow(() -> new NoSuchElementException("Unknown Kafka environment: " + environmentId));

        if (authModule instanceof CertificatesAuthenticationModule) {
            try {
                CertificateSignResult result = ((CertificatesAuthenticationModule) authModule)
                        .createDeveloperCertificateAndPrivateKey(userName).get();
                return new DeveloperCertificateDto(userName + "_" + environmentId + ".p12",
                        Base64.getEncoder().encodeToString(result.getP12Data().orElse(new byte[0])));
            }
            catch (ExecutionException e) {
                throw handleExecutionException(e);
            }
            catch (InterruptedException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
            }

        }
        throw new IllegalStateException(
                "Environment " + environmentId + " does not use Certificates for authentication.");
    }

    @GetMapping(value = "/api/me/certificates/{environmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeveloperCertificateInfoDto getDeveloperCertificateInfo(@PathVariable String environmentId) {
        userService.getCurrentUserName().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        DevCertificateMetadata metadata = certificateService.getDeveloperCertificateOfCurrentUser(environmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        return new DeveloperCertificateInfoDto(metadata.getCertificateDn(), metadata.getExpiryDate());
    }

    private ResponseStatusException handleExecutionException(ExecutionException e) {
        Throwable t = e.getCause();
        if (t instanceof CertificateException) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, t.getMessage());
        }
        if (t instanceof NoSuchElementException) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if ((t instanceof IllegalStateException) || (t instanceof IllegalArgumentException)) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, t.getMessage());
        }

        log.error("Unhandled exception in DeveloperCertificateController", t);
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
