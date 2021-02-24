package com.hermesworld.ais.galapagos.topics.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@Getter
@Setter
public class UpdateTopicConfigEntryDto {

    private String name;

    private String value;

}
