package com.hermesworld.ais.galapagos.topics.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;

@JsonSerialize
@Getter
public class TopicConfigEntryDto {

    private String name;

    private String value;

    private boolean isDefault;

    private boolean readOnly;

    private boolean sensitive;

    public TopicConfigEntryDto(String name, String value, boolean isDefault, boolean readOnly, boolean sensitive) {
        this.name = name;
        this.value = value;
        this.isDefault = isDefault;
        this.readOnly = readOnly;
        this.sensitive = sensitive;
    }

}
