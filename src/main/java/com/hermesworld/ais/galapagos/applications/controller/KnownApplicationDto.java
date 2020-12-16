package com.hermesworld.ais.galapagos.applications.controller;

import java.util.List;

import lombok.Getter;

@Getter
public class KnownApplicationDto {

	private String id;

	private String name;

	private String infoUrl;

	private List<BusinessCapabilityDto> businessCapabilities;

	private List<String> aliases;

	public KnownApplicationDto(String id, String name, String infoUrl, List<BusinessCapabilityDto> businessCapabilities,
			List<String> aliases) {
		this.id = id;
		this.name = name;
		this.infoUrl = infoUrl;
		this.businessCapabilities = businessCapabilities;
		this.aliases = aliases;
	}

}
