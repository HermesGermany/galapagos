package com.hermesworld.ais.galapagos.topics.config;

import java.time.Period;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("galapagos.topics")
@Getter
@Setter
public class GalapagosTopicConfig {

	@Value("topics")
	private String namePrefix;

	@Value(".")
	private String nameSeparator;

	@Value("100")
	private int maxPartitionCount;

	@Value("6")
	private int defaultPartitionCount;

	@Value("P3M")
	private Period minDeprecationTime;

}
