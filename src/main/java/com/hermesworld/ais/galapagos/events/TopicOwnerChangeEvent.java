package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import lombok.Getter;

@Getter
public class TopicOwnerChangeEvent extends TopicEvent {

    private final String previousOwnerApplicationId;

    public TopicOwnerChangeEvent(GalapagosEventContext context, String previousOwnerApplicationId,
            TopicMetadata metadata) {
        super(context, metadata);
        this.previousOwnerApplicationId = previousOwnerApplicationId;

    }

}
