package com.hermesworld.ais.galapagos.kafka.impl;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.kafka.KafkaSender;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import org.json.JSONObject;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TopicBasedRepositoryImplTest {

    private final KafkaSender sender = mock(KafkaSender.class);

    private final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1);

    @After
    public void shutdownExecutor() throws Exception {
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    public void testMessageReceived() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic", "testtopic", ApplicationMetadata.class, sender);
        assertEquals(ApplicationMetadata.class, repository.getValueClass());

        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("app-1");
        metadata.setDn("CN=app1");
        JSONObject val = new JSONObject(JsonUtil.newObjectMapper().writeValueAsString(metadata));
        JSONObject obj = new JSONObject();
        obj.put("obj", val);

        repository.messageReceived("galapagos.testtopic", "app-1", obj.toString());
        assertEquals(1, repository.getObjects().size());
        assertTrue(repository.containsObject("app-1"));
        assertEquals("CN=app1", repository.getObject("app-1").orElseThrow().getDn());
    }

    @Test
    public void testMessageReceived_wrongTopic() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic", "testtopic", ApplicationMetadata.class, sender);

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
    public void testWaitForInitialization_emptyRepository() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic", "testtopic", ApplicationMetadata.class, sender);

        long startTime = System.currentTimeMillis();
        CompletableFuture<Void> future = repository.waitForInitialization(Duration.ofMillis(200), Duration.ofMillis(100), executorService);
        future.get();

        assertTrue("Implementation only waited " + (System.currentTimeMillis() - startTime) + " ms instead of 300 ms",
            System.currentTimeMillis() >= startTime + 300);
        assertFalse(System.currentTimeMillis() >= startTime + 1000);
    }

    @Test
    public void testWaitForInitialization_positive() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic", "testtopic", ApplicationMetadata.class, sender);

        long startTime = System.currentTimeMillis();

        executorService.schedule(() -> repository.messageReceived("galapagos.testtopic", "key", "{\"deleted\": true}"),
            250, TimeUnit.MILLISECONDS);

        CompletableFuture<Void> future = repository.waitForInitialization(Duration.ofMillis(200), Duration.ofMillis(100), executorService);

        future.get();

        assertTrue("Implementation only waited " + (System.currentTimeMillis() - startTime) + " ms instead of 350 ms",
            System.currentTimeMillis() >= startTime + 350);
        assertFalse(System.currentTimeMillis() >= startTime + 1000);
    }

    @Test
    public void testWaitForInitialization_tooLateDataStart() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic", "testtopic", ApplicationMetadata.class, sender);

        long startTime = System.currentTimeMillis();

        executorService.schedule(() -> repository.messageReceived("galapagos.testtopic", "key", "{\"deleted\": true}"),
            350, TimeUnit.MILLISECONDS);

        CompletableFuture<Void> future = repository.waitForInitialization(Duration.ofMillis(200), Duration.ofMillis(100), executorService);

        future.get();

        assertFalse("Repository waited too long", System.currentTimeMillis() >= startTime + 349);
    }

    @Test
    public void testWaitForInitialization_tooLateData() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic", "testtopic", ApplicationMetadata.class, sender);

        long startTime = System.currentTimeMillis();

        executorService.schedule(() -> repository.messageReceived("galapagos.testtopic", "key", "{\"deleted\": true}"), 250, TimeUnit.MILLISECONDS);
        executorService.schedule(() -> repository.messageReceived("galapagos.testtopic", "key", "{\"deleted\": true}"), 400, TimeUnit.MILLISECONDS);

        CompletableFuture<Void> future = repository.waitForInitialization(Duration.ofMillis(200), Duration.ofMillis(100), executorService);

        future.get();

        assertTrue("Implementation only waited " + (System.currentTimeMillis() - startTime) + " ms instead of 350 ms",
            System.currentTimeMillis() >= startTime + 350);
        assertFalse("Repository waited too long", System.currentTimeMillis() >= startTime + 399);
    }

    @Test
    public void testGetTopicName() {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic", "testtopic", ApplicationMetadata.class, sender);
        assertEquals("testtopic", repository.getTopicName());
    }

    @Test
    public void testSave() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic", "testtopic", ApplicationMetadata.class, sender);

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

        assertEquals("app-1", JsonUtil.newObjectMapper().readValue(msg.getJSONObject("obj").toString(), ApplicationMetadata.class).getApplicationId());
    }

    @Test
    public void testDelete() throws Exception {
        TopicBasedRepositoryImpl<ApplicationMetadata> repository = new TopicBasedRepositoryImpl<>("galapagos.testtopic", "testtopic", ApplicationMetadata.class, sender);

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
