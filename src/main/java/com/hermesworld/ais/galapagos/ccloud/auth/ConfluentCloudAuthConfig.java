package com.hermesworld.ais.galapagos.ccloud.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfluentCloudAuthConfig {

    private String environmentId;

    private String clusterId;

    private String clusterApiKey;

    private String clusterApiSecret;

    private String developerApiKeyValidity;

    private String organizationApiKey;

    private String organizationApiSecret;

    private boolean serviceAccountIdCompatMode;

}
