package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import lombok.Getter;

@Getter
public class TopicSchemaRemovedEvent extends TopicEvent {

    public TopicSchemaRemovedEvent(GalapagosEventContext context, TopicMetadata metadata) {
        super(context, metadata);
    }

}
