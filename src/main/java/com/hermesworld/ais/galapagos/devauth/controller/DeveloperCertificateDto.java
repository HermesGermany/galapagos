package com.hermesworld.ais.galapagos.devauth.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;

@JsonSerialize
@Getter
public class DeveloperCertificateDto {

    private final String fileName;

    private final String fileContentsBase64;

    public DeveloperCertificateDto(String fileName, String fileContentsBase64) {
        this.fileName = fileName;
        this.fileContentsBase64 = fileContentsBase64;
    }

}
