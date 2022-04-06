package com.hermesworld.ais.galapagos.tooling;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@Getter
@Setter
public class ToolingAuthenticationMetadata implements HasKey {

    private String applicationName;

    private String authenticationJson;

    @Override
    public String key() {
        return applicationName;
    }

}
