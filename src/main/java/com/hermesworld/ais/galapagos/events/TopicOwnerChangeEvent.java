package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import lombok.Getter;

@Getter
public class TopicOwnerChangeEvent extends TopicEvent {

    private final String newOwnerApplicationId;

    public TopicOwnerChangeEvent(GalapagosEventContext context, String newOwnerApplicationId, TopicMetadata metadata) {
        super(context, metadata);
        this.newOwnerApplicationId = newOwnerApplicationId;

    }
}
