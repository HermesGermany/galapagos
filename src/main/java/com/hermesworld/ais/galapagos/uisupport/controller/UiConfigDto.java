package com.hermesworld.ais.galapagos.uisupport.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.changes.config.ProfilePicture;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@JsonSerialize
@Getter
@Setter
public class UiConfigDto {

    private PeriodDto minDeprecationTime;

    private List<CustomLinkConfig> customLinks;

    private int changelogEntries;

    private int changelogMinDays;

    private ProfilePicture profilePicture;

    private ProfilePicture defaultPicture;

    private String customImageUrl;

}
