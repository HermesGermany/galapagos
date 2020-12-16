package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import lombok.Getter;

@Getter
public class TopicEvent extends AbstractGalapagosEvent {

	private TopicMetadata metadata;

	public TopicEvent(GalapagosEventContext context, TopicMetadata metadata) {
		super(context);
		this.metadata = metadata;
	}

}
