package com.hermesworld.ais.galapagos.topics.controller;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AddProducerDto {

    private List<String> producers; // producerApplicationIds besser

    private String topicName;

}
