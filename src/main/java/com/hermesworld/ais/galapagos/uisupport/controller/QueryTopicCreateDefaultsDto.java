package com.hermesworld.ais.galapagos.uisupport.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.topics.TopicType;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@Getter
@Setter
public class QueryTopicCreateDefaultsDto {

	private TopicType topicType;

	private String applicationId;

	private String environmentId;

	private String businessCapabilityId;

	private Integer expectedMessageCountPerDay;

	private Long expectedAvgMessageSizeBytes;

	private Long retentionTimeMs;

}
