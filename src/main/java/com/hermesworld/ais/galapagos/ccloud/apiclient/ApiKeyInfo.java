package com.hermesworld.ais.galapagos.ccloud.apiclient;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@JsonDeserialize
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Getter
@Setter
public class ApiKeyInfo {

    private int id;

    private String key;

    private String secret;

    private String description;

    private String hashedSecret;

    private String hashFunction;

    private int userId;

    private boolean deactivated;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant created;

    private Instant modified;

    private String accountId;

    private boolean serviceAccount;

}
