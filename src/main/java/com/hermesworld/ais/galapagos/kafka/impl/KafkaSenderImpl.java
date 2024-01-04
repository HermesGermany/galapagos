package com.hermesworld.ais.galapagos.kafka.impl;

import com.hermesworld.ais.galapagos.kafka.KafkaSender;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * Wraps a KafkaTemplate to make concatenated Futures Thread-safe.
 *
 * @author AlbrechtFlo
 *
 */
public class KafkaSenderImpl implements KafkaSender {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final KafkaFutureDecoupler futureDecoupler;

    public KafkaSenderImpl(KafkaTemplate<String, String> template, KafkaFutureDecoupler futureDecoupler) {
        this.kafkaTemplate = template;
        this.futureDecoupler = futureDecoupler;
    }

    @Override
    public CompletableFuture<Void> send(String topic, String key, String message) {
        return futureDecoupler.toCompletableFuture(kafkaTemplate.send(new ProducerRecord<>(topic, key, message)))
                .thenApply(o -> null);
    }

}
