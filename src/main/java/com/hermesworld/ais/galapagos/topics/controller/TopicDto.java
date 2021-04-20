package com.hermesworld.ais.galapagos.topics.controller;

import lombok.Getter;

@Getter
public class TopicDto {

    private String name;

    private String topicType;

    private String environmentId;

    private String description;

    private String externalInterfaceUrl;

    private String ownerApplicationId;

    private boolean deprecated;

    private String deprecationText;

    private String eolDate;

    private boolean subscriptionApprovalRequired;

    private boolean deletable;

    public TopicDto(String name, String topicType, String environmentId, String description,
            String externalInterfaceUrl, String ownerApplicationId, boolean deprecated, String deprecationText,
            String eolDate, boolean subscriptionApprovalRequired, boolean deletable) {
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
    }

}
