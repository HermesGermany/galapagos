package com.hermesworld.ais.galapagos.applications.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@Getter
@Setter
public class ServiceAccountIdDto {

    private final Integer accountId;

    public ServiceAccountIdDto(Integer accountId) {
        this.accountId = accountId;
    }
}
