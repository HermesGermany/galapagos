package com.hermesworld.ais.galapagos.kafka.util;

import com.hermesworld.ais.galapagos.kafka.KafkaCluster;

/**
 * Interface for components which want to "init" something per connected Kafka Cluster on startup. Usually, this means
 * getting their required topic based repository for the first time, so the repository starts receiving async data and
 * is caught by the startup listener which waits for all repositories until they have received initial data.
 */
public interface InitPerCluster {

	/**
	 * Called during Galapagos startup, once for each connected Kafka cluster.
	 *
	 * @param cluster Cluster to perform startup initialization on (e.g., initialize a repository).
	 */
	void init(KafkaCluster cluster);

}
