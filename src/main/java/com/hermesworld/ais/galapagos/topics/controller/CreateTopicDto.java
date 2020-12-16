package com.hermesworld.ais.galapagos.topics.controller;

import java.util.Map;

import com.hermesworld.ais.galapagos.topics.TopicType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTopicDto {

    private String name;

    private TopicType topicType;

    private String description;

    private String ownerApplicationId;

    private boolean subscriptionApprovalRequired;

    private Integer partitionCount;

    private Map<String, String> topicConfig;

}
