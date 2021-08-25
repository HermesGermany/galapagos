package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import lombok.Getter;

@Getter
public class TopicAddProducerEvent extends TopicEvent {

    private final String producerApplicationId;

    public TopicAddProducerEvent(GalapagosEventContext context, String producerApplicationId,
            TopicMetadata metadata) {
        super(context, metadata);
        this.producerApplicationId = producerApplicationId;
    }

}
