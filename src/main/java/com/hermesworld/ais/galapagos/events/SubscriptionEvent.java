package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import lombok.Getter;

@Getter
public class SubscriptionEvent extends AbstractGalapagosEvent {

	private SubscriptionMetadata metadata;

	public SubscriptionEvent(GalapagosEventContext context, SubscriptionMetadata metadata) {
		super(context);
		this.metadata = metadata;
	}

}
