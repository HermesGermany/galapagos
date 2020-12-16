package com.hermesworld.ais.galapagos.kafka;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class TopicCreateParams {

	private int numberOfPartitions;

	private int replicationFactor;

	private Map<String, String> topicConfigs;

	public TopicCreateParams(int numberOfPartitions, int replicationFactor) {
		this(numberOfPartitions, replicationFactor, Collections.emptyMap());
	}

	public TopicCreateParams(int numberOfPartitions, int replicationFactor, Map<String, String> topicConfigs) {
		this.numberOfPartitions = numberOfPartitions;
		this.replicationFactor = replicationFactor;
		this.topicConfigs = new HashMap<>(topicConfigs);
	}

	public void setTopicConfig(String configKey, String configValue) {
		topicConfigs.put(configKey, configValue);
	}

	public int getNumberOfPartitions() {
		return numberOfPartitions;
	}

	public int getReplicationFactor() {
		return replicationFactor;
	}

	/**
	 * Returns custom Kafka configuration properties for this topic.
	 *
	 * @return Custom Kafka configuration properties for this topic, maybe an empty map, but never <code>null</code>.
	 */
	public Map<String, String> getTopicConfigs() {
		return Collections.unmodifiableMap(topicConfigs);
	}

}
