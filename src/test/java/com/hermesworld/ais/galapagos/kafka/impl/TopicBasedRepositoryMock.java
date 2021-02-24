package com.hermesworld.ais.galapagos.kafka.impl;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.CheckReturnValue;

import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.util.HasKey;

public class TopicBasedRepositoryMock<T extends HasKey> implements TopicBasedRepository<T> {

    private final String topicName;

    private final Class<T> valueClass;

    private final Map<String, T> data = new HashMap<>();

    public TopicBasedRepositoryMock() {
        this("unknown-topic", null);
    }

    public TopicBasedRepositoryMock(String topicName, Class<T> valueClass) {
        this.topicName = topicName;
        this.valueClass = valueClass;
    }

    @Override
    public String getTopicName() {
        return topicName;
    }

    @Override
    public Class<T> getValueClass() {
        return valueClass;
    }

    @Override
    public boolean containsObject(String id) {
        return data.containsKey(id);
    }

    @Override
    public Optional<T> getObject(String id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public Collection<T> getObjects() {
        return data.values();
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> save(T value) {
        data.put(value.key(), value);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> delete(T value) {
        data.remove(value.key());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    @CheckReturnValue
    public CompletableFuture<Void> waitForInitialization(Duration initialWaitTime, Duration idleTime,
            ScheduledExecutorService executorService) {
        return CompletableFuture.completedFuture(null);
    }
}
