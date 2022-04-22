package com.hermesworld.ais.galapagos.uisupport.controller;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@Getter
@Setter
public class UiConfigDto {

    private PeriodDto minDeprecationTime;

    private List<CustomLinkConfig> customLinks;

    private int changelogEntries;

    private int changelogMinDays;

}
