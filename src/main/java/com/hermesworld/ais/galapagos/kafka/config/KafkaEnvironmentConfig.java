package com.hermesworld.ais.galapagos.kafka.config;

import java.time.Duration;

import org.springframework.core.io.Resource;

public interface KafkaEnvironmentConfig {

	String getId();

	String getName();

	String getBootstrapServers();

	Resource getCaCertificateFile();

	Resource getCaKeyFile();

	/**
	 * Returns the validity for client application certificates created for this environment, in a format accepted by
	 * {@link Duration#parse(CharSequence)}.
	 *
	 * @return The validity for client application certificates created for this environment, as a duration string, or
	 * <code>null</code> to use the default.
	 */
	String getApplicationCertificateValidity();

	/**
	 * Returns the validity for developer client certificates created for this environment, in a format accepted by
	 * {@link Duration#parse(CharSequence)}.
	 *
	 * @return The validity for developer client certificates created for this environment, as a duration string, or
	 * <code>null</code> to use the default. A value of 0 or lower indicates that developer certificates are disabled for
	 * this environment.
	 */
	String getDeveloperCertificateValidity();

	/**
	 * This DN must be configured in the Kafka Cluster to be allowed to perform all operations necessary for the operation
	 * of Galapagos (usually, just ALL operations). This must be done manually once per Cluster. <br>
	 * Galapagos will create a self-signed client certificate on the fly containing this DN for logging in to this cluster.
	 *
	 * @return The DN configured in this Kafka Cluster for the Galapagos Client.
	 */
	String getClientDn();

	/**
	 * If <code>true</code>, direct changes on this environment are not allowed (topics / subscriptions). You must use
	 * staging functionality from the preceding environment stage to get changes on this environment.
	 *
	 * @return <code>true</code> if no direct changes are allowed on this environment, <code>false</code> otherwise.
	 */
	boolean isStagingOnly();

}
