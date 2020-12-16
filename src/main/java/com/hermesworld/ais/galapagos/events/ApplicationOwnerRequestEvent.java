package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import lombok.Getter;

@Getter
public class ApplicationOwnerRequestEvent extends AbstractGalapagosEvent {

	private ApplicationOwnerRequest request;

	public ApplicationOwnerRequestEvent(GalapagosEventContext context, ApplicationOwnerRequest request) {
		super(context);
		this.request = request;
	}

}
