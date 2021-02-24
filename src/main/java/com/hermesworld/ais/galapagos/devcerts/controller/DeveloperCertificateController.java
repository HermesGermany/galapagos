package com.hermesworld.ais.galapagos.devcerts.controller;

import java.io.ByteArrayOutputStream;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.hermesworld.ais.galapagos.devcerts.DevCertificateMetadata;
import com.hermesworld.ais.galapagos.devcerts.DeveloperCertificateService;
import com.hermesworld.ais.galapagos.security.CurrentUserService;

@RestController
@Slf4j
public class DeveloperCertificateController {

    private DeveloperCertificateService certificateService;

    private CurrentUserService userService;

    public DeveloperCertificateController(DeveloperCertificateService certificateService,
            CurrentUserService userService) {
        this.certificateService = certificateService;
        this.userService = userService;
    }

    @PostMapping(value = "/api/me/certificates/{environmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeveloperCertificateDto createDeveloperCertificate(@PathVariable String environmentId) {
        String userName = userService.getCurrentUserName()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            certificateService.createDeveloperCertificateForCurrentUser(environmentId, baos).get();
            DeveloperCertificateDto result = new DeveloperCertificateDto(userName + "_" + environmentId + ".p12",
                    Base64.getEncoder().encodeToString(baos.toByteArray()));
            return result;
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
        catch (InterruptedException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
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
