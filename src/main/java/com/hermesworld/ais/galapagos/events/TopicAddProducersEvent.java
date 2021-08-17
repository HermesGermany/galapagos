package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import lombok.Getter;

import java.util.List;

@Getter
public class TopicAddProducersEvent extends TopicEvent {

    private final List<String> producerApplicationIds;

    public TopicAddProducersEvent(GalapagosEventContext context, List<String> producerApplicationIds,
            TopicMetadata metadata) {
        super(context, metadata);
        this.producerApplicationIds = List.copyOf(producerApplicationIds);
    }

}
