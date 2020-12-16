package com.hermesworld.ais.galapagos.applications.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "galapagos.applications")
public class ApplicationsConfig {

	private String consumerGroupPrefix;
	
	private String topicPrefixFormat;

	public String getConsumerGroupPrefix() {
		return consumerGroupPrefix;
	}

	public void setConsumerGroupPrefix(String consumerGroupPrefix) {
		this.consumerGroupPrefix = consumerGroupPrefix;
	}

	public String getTopicPrefixFormat() {
		return topicPrefixFormat;
	}

	public void setTopicPrefixFormat(String topicPrefixFormat) {
		this.topicPrefixFormat = topicPrefixFormat;
	}

}
