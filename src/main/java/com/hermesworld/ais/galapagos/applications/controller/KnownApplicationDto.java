package com.hermesworld.ais.galapagos.applications.controller;

import lombok.Getter;

import java.util.List;

@Getter
public class KnownApplicationDto {

    private final String id;

    private final String name;

    private final String infoUrl;

    private final List<BusinessCapabilityDto> businessCapabilities;

    private final List<String> aliases;

    private final boolean valid;

    public KnownApplicationDto(String id, String name, String infoUrl, List<BusinessCapabilityDto> businessCapabilities,
            List<String> aliases, boolean valid) {
        this.id = id;
        this.name = name;
        this.infoUrl = infoUrl;
        this.businessCapabilities = businessCapabilities;
        this.aliases = aliases;
        this.valid = valid;
    }

}
