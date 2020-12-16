package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.kafka.KafkaCluster;

public interface GalapagosEventManager {

	GalapagosEventSink newEventSink(KafkaCluster kafkaCluster);

}
