package com.hermesworld.ais.galapagos.uisupport.controller;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "galapagos.custom-links")
@Configuration
public class CustomLinksConfig {

	@Getter
	private List<CustomLinkConfig> links = new ArrayList<>();

	public void setLinks(List<CustomLinkConfig> links) {
		checkElements(links);
		this.links = links;
	}

	private void checkElements(List<CustomLinkConfig> links) throws RuntimeException {

		for (CustomLinkConfig customLinkConfig : links) {
			if (customLinkConfig.getId() == null || customLinkConfig.getHref() == null || customLinkConfig.getLabel() == null
					|| customLinkConfig.getLinkType() == null) {
				throw new RuntimeException("A field of a custom link must not be empty. Please check application.properties.");
			}
		}

	}

}
