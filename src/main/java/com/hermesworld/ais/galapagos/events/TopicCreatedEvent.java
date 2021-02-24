package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import lombok.Getter;

@Getter
public class TopicCreatedEvent extends TopicEvent {

    private TopicCreateParams topicCreateParams;

    public TopicCreatedEvent(GalapagosEventContext context, TopicMetadata metadata,
            TopicCreateParams topicCreateParams) {
        super(context, metadata);
        this.topicCreateParams = topicCreateParams;
    }

}
