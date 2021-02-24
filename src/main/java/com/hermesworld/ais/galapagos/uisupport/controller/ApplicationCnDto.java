package com.hermesworld.ais.galapagos.uisupport.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonSerialize
public class ApplicationCnDto {

    private String applicationId;

    private String name;

    private String cn;

    public ApplicationCnDto(String applicationId, String name, String cn) {
        this.applicationId = applicationId;
        this.name = name;
        this.cn = cn;
    }

}
