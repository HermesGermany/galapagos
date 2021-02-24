package com.hermesworld.ais.galapagos.topics.controller;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddSchemaVersionDto {

    private String jsonSchema;

    private String changeDescription;

}
