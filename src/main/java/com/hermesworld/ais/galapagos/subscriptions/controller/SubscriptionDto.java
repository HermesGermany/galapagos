package com.hermesworld.ais.galapagos.subscriptions.controller;

import com.hermesworld.ais.galapagos.subscriptions.SubscriptionState;
import lombok.Getter;

@Getter
public class SubscriptionDto {

    private final String id;

    private final String topicName;

    private final String environmentId;

    private final String clientApplicationId;

    private final SubscriptionState state;

    private final String description;

    public SubscriptionDto(String id, String topicName, String environmentId, String clientApplicationId,
            SubscriptionState state, String description) {
        this.id = id;
        this.topicName = topicName;
        this.environmentId = environmentId;
        this.clientApplicationId = clientApplicationId;
        this.state = state;
        this.description = description;
    }

}
