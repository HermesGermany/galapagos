package com.hermesworld.ais.galapagos.applications.controller;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BusinessCapabilityDto {

    private String id;

    private String name;

    public BusinessCapabilityDto(String id, String name) {
        this.id = id;
        this.name = name;
    }

}
