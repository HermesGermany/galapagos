package com.hermesworld.ais.galapagos.applications;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.util.HasKey;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonSerialize
public class ApplicationMetadata implements HasKey {

	private String applicationId;

	private String dn;

	// TODO should be Instant
	private ZonedDateTime certificateExpiresAt;

	private List<String> consumerGroupPrefixes;

	private String topicPrefix;

	public ApplicationMetadata() {
	}

	public ApplicationMetadata(ApplicationMetadata original) {
		this.applicationId = original.applicationId;
		this.dn = original.dn;
		this.certificateExpiresAt = original.certificateExpiresAt;
		this.consumerGroupPrefixes = original.consumerGroupPrefixes;
		this.topicPrefix = original.topicPrefix;
	}

	@Override
	public String key() {
		return applicationId;
	}

}
