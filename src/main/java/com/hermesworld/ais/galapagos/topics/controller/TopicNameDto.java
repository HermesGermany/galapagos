package com.hermesworld.ais.galapagos.topics.controller;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TopicNameDto {

	private String name;

	public TopicNameDto(String name) {
		this.name = name;
	}

}
