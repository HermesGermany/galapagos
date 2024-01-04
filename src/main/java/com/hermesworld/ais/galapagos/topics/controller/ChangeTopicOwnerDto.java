package com.hermesworld.ais.galapagos.topics.controller;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotNull;

@Getter
@Setter
public class ChangeTopicOwnerDto {
    @NotNull(message = "producer id cannot be null!")
    private String producerApplicationId;
}
