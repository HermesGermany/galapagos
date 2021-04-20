package com.hermesworld.ais.galapagos.topics.controller;

import com.hermesworld.ais.galapagos.topics.Criticality;
import com.hermesworld.ais.galapagos.topics.MessagesPerDay;
import com.hermesworld.ais.galapagos.topics.MessagesSize;
import com.hermesworld.ais.galapagos.topics.TopicType;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

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

    private long compactionTimeMillis;

    private long retentionTimeMillis;

    private Criticality criticality;

    private MessagesPerDay messagesPerDay;

    private MessagesSize messagesSize;
}
