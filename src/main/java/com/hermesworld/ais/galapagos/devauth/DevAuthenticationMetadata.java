package com.hermesworld.ais.galapagos.devauth;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@Getter
@Setter
public class DevAuthenticationMetadata implements HasKey {

    private String userName;

    private String authenticationJson;

    @Override
    public String key() {
        return userName;
    }

}
