package com.hermesworld.ais.galapagos.events;

import java.util.Optional;

import com.hermesworld.ais.galapagos.kafka.KafkaCluster;

public interface GalapagosEventContext {

    KafkaCluster getKafkaCluster();

    <T> Optional<T> getContextValue(String key);

}
