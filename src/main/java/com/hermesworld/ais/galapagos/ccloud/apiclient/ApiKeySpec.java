package com.hermesworld.ais.galapagos.ccloud.apiclient;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class ApiKeySpec {

    private String id;

    private String secret;

    private String description;

    private Instant createdAt;

    private String serviceAccountId;

}
