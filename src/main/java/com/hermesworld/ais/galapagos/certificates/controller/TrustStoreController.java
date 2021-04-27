package com.hermesworld.ais.galapagos.certificates.controller;

import com.hermesworld.ais.galapagos.certificates.CertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrustStoreController {

    private final CertificateService certificateService;

    @Autowired
    public TrustStoreController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    // intentionally not protected - no /api path
    @GetMapping(value = "/files/truststore", produces = "application/x-pkcs12")
    public ResponseEntity<byte[]> getTrustStore() {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=kafka-truststore.jks");
        return new ResponseEntity<>(certificateService.getTrustStorePkcs12(), responseHeaders, HttpStatus.OK);
    }

}
