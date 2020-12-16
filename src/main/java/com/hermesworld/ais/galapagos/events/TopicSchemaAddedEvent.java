package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import lombok.Getter;

@Getter
public class TopicSchemaAddedEvent extends TopicEvent {

	private SchemaMetadata newSchema;

	public TopicSchemaAddedEvent(GalapagosEventContext context, TopicMetadata metadata, SchemaMetadata newSchema) {
		super(context, metadata);
		this.newSchema = newSchema;
	}

}
