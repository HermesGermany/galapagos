package com.hermesworld.ais.galapagos.uisupport.controller;

import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@Getter
@Setter
public class TopicCreateDefaultsDto {

	private int defaultPartitionCount;

	private Map<String, String> defaultTopicConfigs;

	private String topicNameSuggestion;

}
