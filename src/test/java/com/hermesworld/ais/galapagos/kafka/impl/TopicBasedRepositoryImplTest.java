package com.hermesworld.ais.galapagos.kafka.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.kafka.KafkaSender;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopicBasedRepositoryImplTest {

    private final KafkaSender sender = mock(KafkaSender.class);

    private final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1);

    @AfterEach
    void shutdownExecutor() throws Exception {
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    void testMessageReceived() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic",
                "testtopic", ApplicationMetadata.class, sender);
        assertEquals(ApplicationMetadata.class, repository.getValueClass());

        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("app-1");
        metadata.setAuthenticationJson(new JSONObject(Map.of("dn", "CN=app1")).toString());
        JSONObject val = new JSONObject(JsonUtil.newObjectMapper().writeValueAsString(metadata));
        JSONObject obj = new JSONObject();
        obj.put("obj", val);

        repository.messageReceived("galapagos.testtopic", "app-1", obj.toString());
        assertEquals(1, repository.getObjects().size());
        assertTrue(repository.containsObject("app-1"));
        assertEquals("CN=app1",
                new JSONObject(repository.getObject("app-1").orElseThrow().getAuthenticationJson()).getString("dn"));
    }

    @Test
    void testMessageReceived_wrongTopic() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic",
                "testtopic", ApplicationMetadata.class, sender);

        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("app-1");
        JSONObject val = new JSONObject(JsonUtil.newObjectMapper().writeValueAsString(metadata));
        JSONObject obj = new JSONObject();
        obj.put("obj", val);

        repository.messageReceived("testtopic", "app-1", obj.toString());
        assertEquals(0, repository.getObjects().size());
        assertFalse(repository.containsObject("app-1"));
    }

    @Test
    void testWaitForInitialization_emptyRepository() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic",
                "testtopic", ApplicationMetadata.class, sender);

        long startTime = System.currentTimeMillis();
        CompletableFuture<Void> future = repository.waitForInitialization(Duration.ofMillis(200),
                Duration.ofMillis(100), executorService);
        future.get();

        assertTrue(System.currentTimeMillis() >= startTime + 300,
                "Implementation only waited " + (System.currentTimeMillis() - startTime) + " ms instead of 300 ms");
        assertFalse(System.currentTimeMillis() >= startTime + 1000);
    }

    @Test
    void testWaitForInitialization_positive() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic",
                "testtopic", ApplicationMetadata.class, sender);

        long startTime = System.currentTimeMillis();

        executorService.schedule(() -> repository.messageReceived("galapagos.testtopic", "key", "{\"deleted\": true}"),
                250, TimeUnit.MILLISECONDS);

        CompletableFuture<Void> future = repository.waitForInitialization(Duration.ofMillis(200),
                Duration.ofMillis(100), executorService);

        future.get();

        assertTrue(System.currentTimeMillis() >= startTime + 350,
                "Implementation only waited " + (System.currentTimeMillis() - startTime) + " ms instead of 350 ms");
        assertFalse(System.currentTimeMillis() >= startTime + 1000);
    }

    @Test
    void testWaitForInitialization_tooLateDataStart() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic",
                "testtopic", ApplicationMetadata.class, sender);

        long startTime = System.currentTimeMillis();

        executorService.schedule(() -> repository.messageReceived("galapagos.testtopic", "key", "{\"deleted\": true}"),
                350, TimeUnit.MILLISECONDS);

        CompletableFuture<Void> future = repository.waitForInitialization(Duration.ofMillis(200),
                Duration.ofMillis(100), executorService);

        future.get();

        assertFalse(System.currentTimeMillis() >= startTime + 349, "Repository waited too long");
    }

    @Test
    void testWaitForInitialization_tooLateData() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic",
                "testtopic", ApplicationMetadata.class, sender);

        long startTime = System.currentTimeMillis();

        executorService.schedule(() -> repository.messageReceived("galapagos.testtopic", "key", "{\"deleted\": true}"),
                250, TimeUnit.MILLISECONDS);
        executorService.schedule(() -> repository.messageReceived("galapagos.testtopic", "key", "{\"deleted\": true}"),
                400, TimeUnit.MILLISECONDS);

        CompletableFuture<Void> future = repository.waitForInitialization(Duration.ofMillis(200),
                Duration.ofMillis(100), executorService);

        future.get();

        assertTrue(System.currentTimeMillis() >= startTime + 350,
                "Implementation only waited " + (System.currentTimeMillis() - startTime) + " ms instead of 350 ms");
        assertFalse(System.currentTimeMillis() >= startTime + 399, "Repository waited too long");
    }

    @Test
    void testGetTopicName() {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic",
                "testtopic", ApplicationMetadata.class, sender);
        assertEquals("testtopic", repository.getTopicName());
    }

    @Test
    void testSave() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic",
                "testtopic", ApplicationMetadata.class, sender);

        AtomicReference<String> topicName = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> message = new AtomicReference<>();

        when(sender.send(any(), any(), any())).then(inv -> {
            topicName.set(inv.getArgument(0));
            key.set(inv.getArgument(1));
            message.set(inv.getArgument(2));
            return FutureUtil.noop();
        });

        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("app-1");

        repository.save(metadata).get();

        assertEquals("galapagos.testtopic", topicName.get());
        assertEquals("app-1", key.get());

        JSONObject msg = new JSONObject(message.get());

        assertEquals("app-1", JsonUtil.newObjectMapper()
                .readValue(msg.getJSONObject("obj").toString(), ApplicationMetadata.class).getApplicationId());
    }

    @Test
    void testDelete() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic",
                "testtopic", ApplicationMetadata.class, sender);

        AtomicReference<String> topicName = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> message = new AtomicReference<>();

        when(sender.send(any(), any(), any())).then(inv -> {
            topicName.set(inv.getArgument(0));
            key.set(inv.getArgument(1));
            message.set(inv.getArgument(2));
            return FutureUtil.noop();
        });

        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("app-1");

        repository.delete(metadata).get();

        assertEquals("galapagos.testtopic", topicName.get());
        assertEquals("app-1", key.get());

        JSONObject msg = new JSONObject(message.get());

        assertTrue(msg.getBoolean("deleted"));
    }

}
