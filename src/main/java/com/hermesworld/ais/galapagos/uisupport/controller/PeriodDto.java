package com.hermesworld.ais.galapagos.uisupport.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@Getter
@Setter
public class PeriodDto {

    private int years;

    private int months;

    private int days;

    public PeriodDto(int years, int months, int days) {
        this.years = years;
        this.months = months;
        this.days = days;
    }

    public PeriodDto() {
    }
}
