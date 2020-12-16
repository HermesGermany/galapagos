package com.hermesworld.ais.galapagos.kafka.config.impl;

import lombok.Getter;
import lombok.Setter;
import org.springframework.core.io.Resource;

import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;

@Setter
@Getter(onMethod=@__({@Override}))
public class KafkaEnvironmentConfigImpl implements KafkaEnvironmentConfig {

	private String id;

	private String name;

	private String bootstrapServers;

	private Resource caCertificateFile;

	private Resource caKeyFile;

	private String applicationCertificateValidity;

	private String developerCertificateValidity;

	private String clientDn;

	private boolean stagingOnly;

}
