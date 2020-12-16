package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import lombok.Getter;

@Getter
public class ApplicationCertificateChangedEvent extends ApplicationEvent {

	private String previousDn;

	public ApplicationCertificateChangedEvent(GalapagosEventContext context, ApplicationMetadata metadata, String previousDn) {
		super(context, metadata);
		this.previousDn = previousDn;
	}

}
