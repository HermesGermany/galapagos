package com.hermesworld.ais.galapagos.topics.controller;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

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

    public UpdateTopicDto(String deprecationText, LocalDate eolDate, String description) {
        this.deprecationText = deprecationText;
        this.eolDate = eolDate;
        this.description = description;
    }
}
