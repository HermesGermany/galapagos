package com.hermesworld.ais.galapagos.subscriptions.controller;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSubscriptionDto {

	private String topicName;

	private String description;

	public CreateSubscriptionDto() {
	}

}
