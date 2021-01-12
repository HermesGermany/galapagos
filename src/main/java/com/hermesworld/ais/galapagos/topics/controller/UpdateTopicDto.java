package com.hermesworld.ais.galapagos.topics.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@JsonSerialize
public class UpdateTopicDto {

    private String deprecationText;

    private String description;

    private boolean updateDescription;

    private LocalDate eolDate;

    public UpdateTopicDto() {

    }

    public UpdateTopicDto(String deprecationText, LocalDate eolDate, String description, boolean updateDescription) {
        this.deprecationText = deprecationText;
        this.eolDate = eolDate;
        this.description = description;
        this.updateDescription = updateDescription;
    }
}
