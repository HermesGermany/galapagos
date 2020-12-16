package com.hermesworld.ais.galapagos.applications.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.applications.BusinessCapability;

@JsonSerialize
public class BusinessCapabilityImpl implements BusinessCapability {

	private String id;

	private String name;

	@JsonCreator
	public BusinessCapabilityImpl(@JsonProperty(value = "id", required = true) String id,
			@JsonProperty(value = "name", required = true) String name) {
		this.id = id;
		this.name = name;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	}

}
