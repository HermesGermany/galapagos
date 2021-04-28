package com.hermesworld.ais.galapagos.ccloud.auth;

import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationInfo;
import lombok.Getter;

@Getter
public class ConfluentCloudAuthenticationInfo implements KafkaAuthenticationInfo {

    private final String apiKey;

    private final String secret;

    public ConfluentCloudAuthenticationInfo(String apiKey, String secret) {
        this.apiKey = apiKey;
        this.secret = secret;
    }
}
