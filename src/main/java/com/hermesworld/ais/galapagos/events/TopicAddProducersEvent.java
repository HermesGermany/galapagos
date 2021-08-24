package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import lombok.Getter;

@Getter
public class TopicAddProducersEvent extends TopicEvent {

    private final String producerApplicationId;

    public TopicAddProducersEvent(GalapagosEventContext context, String producerApplicationId,
            TopicMetadata metadata) {
        super(context, metadata);
        this.producerApplicationId = producerApplicationId;
    }

}
