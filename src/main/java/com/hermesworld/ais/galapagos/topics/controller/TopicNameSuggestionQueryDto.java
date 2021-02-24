package com.hermesworld.ais.galapagos.topics.controller;

import com.hermesworld.ais.galapagos.topics.TopicType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TopicNameSuggestionQueryDto {

    private TopicType topicType;

    private String applicationId;

    private String environmentId;

    private String businessCapabilityId;

}
