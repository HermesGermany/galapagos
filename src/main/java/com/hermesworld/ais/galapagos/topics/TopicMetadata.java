package com.hermesworld.ais.galapagos.topics;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class TopicMetadata implements HasKey {

	private String name;

	private TopicType type;

	private String description;

	/**
	 * Can be set from external tooling via Kafka Topic galapagos.internal.topics
	 */
	private String infoUrl;

	private String ownerApplicationId;

	private boolean isDeprecated;

	private String deprecationText;

	@JsonFormat(shape = Shape.STRING)
	private LocalDate eolDate;

	private boolean subscriptionApprovalRequired;

	public TopicMetadata() {
	}

	public TopicMetadata(TopicMetadata original) {
		this.name = original.name;
		this.type = original.type;
		this.description = original.description;
		this.infoUrl = original.infoUrl;
		this.ownerApplicationId = original.ownerApplicationId;
		this.isDeprecated = original.isDeprecated;
		this.deprecationText = original.deprecationText;
		this.eolDate = original.eolDate;
		this.subscriptionApprovalRequired = original.subscriptionApprovalRequired;
	}

	@Override
	public String key() {
		return name;
	}

}
