package com.hermesworld.ais.galapagos.kafka.impl;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaException;

public interface KafkaConsumerFactory<K, V> {

    KafkaConsumer<K, V> newConsumer() throws KafkaException;

}
