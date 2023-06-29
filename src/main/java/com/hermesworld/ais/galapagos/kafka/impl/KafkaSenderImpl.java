package com.hermesworld.ais.galapagos.kafka.impl;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

import com.hermesworld.ais.galapagos.kafka.KafkaSender;

/**
 * Wraps a KafkaTemplate to make concatenated Futures Thread-safe.
 *
 * @author AlbrechtFlo
 *
 */
public class KafkaSenderImpl implements KafkaSender {

    private KafkaTemplate<String, String> kafkaTemplate;

    public KafkaSenderImpl(KafkaTemplate<String, String> template) {
        this.kafkaTemplate = template;
    }

    @Override
    public CompletableFuture<Void> send(String topic, String key, String message) {
        return kafkaTemplate.send(new ProducerRecord<>(topic, key, message)).thenApply(o -> null);
    }

}
