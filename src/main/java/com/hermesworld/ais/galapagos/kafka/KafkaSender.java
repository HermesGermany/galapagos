package com.hermesworld.ais.galapagos.kafka;

import java.util.concurrent.CompletableFuture;

/**
 * Galapagos-specific abstraction of the KafkaTemplate class of Spring (or a different sender implementation). This
 * abstraction is used to be able to build a Thread-safe variant around the KafkaTemplate class (which causes some
 * strange blockings when concatenating futures and sending from their completion stages). <br>
 * Also, it allows for easier unit testing.
 *
 * @author AlbrechtFlo
 *
 */
public interface KafkaSender {

	CompletableFuture<Void> send(String topic, String key, String message);

}
