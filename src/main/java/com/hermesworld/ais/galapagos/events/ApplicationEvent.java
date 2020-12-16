package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import lombok.Getter;

@Getter
public class ApplicationEvent extends AbstractGalapagosEvent {

	private ApplicationMetadata metadata;

	public ApplicationEvent(GalapagosEventContext context, ApplicationMetadata metadata) {
		super(context);
		this.metadata = metadata;
	}

}
