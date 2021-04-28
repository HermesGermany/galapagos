package com.hermesworld.ais.galapagos.kafka.auth;

import lombok.Getter;
import org.json.JSONObject;

@Getter
public class CreateAuthenticationResult {

    private final JSONObject publicAuthenticationData;

    private final byte[] privateAuthenticationData;

    public CreateAuthenticationResult(JSONObject publicAuthenticationData, byte[] privateAuthenticationData) {
        this.publicAuthenticationData = publicAuthenticationData;
        this.privateAuthenticationData = new byte[privateAuthenticationData.length];
        System.arraycopy(privateAuthenticationData, 0, this.privateAuthenticationData, 0,
                privateAuthenticationData.length);
    }
}
