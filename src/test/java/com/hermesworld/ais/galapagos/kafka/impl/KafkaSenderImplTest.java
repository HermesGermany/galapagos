package com.hermesworld.ais.galapagos.kafka.impl;

import com.hermesworld.ais.galapagos.kafka.KafkaExecutorFactory;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KafkaSenderImplTest {

    private static final ThreadFactory tfDecoupled = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "decoupled-" + System.currentTimeMillis());
        }
    };

    private static final KafkaExecutorFactory executorFactory = () -> {
        return Executors.newSingleThreadExecutor(tfDecoupled);
    };

    @Test
    public void testSendDecoupling() throws Exception {
        KafkaFutureDecoupler decoupler = new KafkaFutureDecoupler(executorFactory);

        @SuppressWarnings("unchecked")
        Producer<String, String> producer = mock(Producer.class);
        when(producer.send(any(), any())).then(inv -> {
            return CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(200);
                }
                catch (InterruptedException e) {
                }
                Callback cb = inv.getArgument(1);
                cb.onCompletion(new RecordMetadata(new TopicPartition("a", 0), 0, 0, 0, null, 0, 0), null);
            });
        });

        ProducerFactory<String, String> factory = () -> {
            return producer;
        };

        KafkaTemplate<String, String> template = new KafkaTemplate<String, String>(factory);

        KafkaSenderImpl sender = new KafkaSenderImpl(template, decoupler);

        StringBuilder threadName = new StringBuilder();

        sender.send("a", "b", "c").thenApply(o -> threadName.append(Thread.currentThread().getName())).get();
        assertTrue(threadName.toString().startsWith("decoupled-"));
    }

}
