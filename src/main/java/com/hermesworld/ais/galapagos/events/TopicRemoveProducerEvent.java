package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import lombok.Getter;

@Getter
public class TopicRemoveProducerEvent extends TopicEvent {

    private final String producerToBeDeletedId;

    public TopicRemoveProducerEvent(GalapagosEventContext context, String producerToBeDeletedId,
            TopicMetadata metadata) {
        super(context, metadata);
        this.producerToBeDeletedId = producerToBeDeletedId;

    }
}
