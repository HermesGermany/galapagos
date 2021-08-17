package com.hermesworld.ais.galapagos.topics.controller;

import com.hermesworld.ais.galapagos.topics.Criticality;
import com.hermesworld.ais.galapagos.topics.MessagesPerDay;
import com.hermesworld.ais.galapagos.topics.MessagesSize;
import lombok.Getter;

import java.util.List;

@Getter
public class TopicDto {

    private final String name;

    private final String topicType;

    private final String environmentId;

    private final String description;

    private final String externalInterfaceUrl;

    private final String ownerApplicationId;

    private final boolean deprecated;

    private final String deprecationText;

    private final String eolDate;

    private final boolean subscriptionApprovalRequired;

    private final boolean deletable;

    private final long compactionTimeMillis;

    private final long retentionTimeMillis;

    private final Criticality criticality;

    private final MessagesPerDay messagesPerDay;

    private final MessagesSize messagesSize;

    private final List<String> producers;

    public TopicDto(String name, String topicType, String environmentId, String description,
            String externalInterfaceUrl, String ownerApplicationId, boolean deprecated, String deprecationText,
            String eolDate, boolean subscriptionApprovalRequired, boolean deletable, long compactionTimeMillis,
            long retentionTimeMillis, Criticality criticality, MessagesPerDay messagesPerDay,
            MessagesSize messagesSize, List<String> producers) {
        this.name = name;
        this.topicType = topicType;
        this.environmentId = environmentId;
        this.description = description;
        this.externalInterfaceUrl = externalInterfaceUrl;
        this.ownerApplicationId = ownerApplicationId;
        this.deprecated = deprecated;
        this.deprecationText = deprecationText;
        this.eolDate = eolDate;
        this.subscriptionApprovalRequired = subscriptionApprovalRequired;
        this.deletable = deletable;
        this.compactionTimeMillis = compactionTimeMillis;
        this.retentionTimeMillis = retentionTimeMillis;
        this.criticality = criticality;
        this.messagesPerDay = messagesPerDay;
        this.messagesSize = messagesSize;
        this.producers = producers;
    }

}
