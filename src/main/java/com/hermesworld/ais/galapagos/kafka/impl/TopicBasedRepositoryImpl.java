package com.hermesworld.ais.galapagos.kafka.impl;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermesworld.ais.galapagos.kafka.KafkaSender;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.util.HasKey;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

final class TopicBasedRepositoryImpl<T extends HasKey> implements TopicBasedRepository<T> {

	// TODO currently, if T is a mutable type, callers could modify our internal storage from outside.
	// The internal map should store the JSON object representation instead and freshly generate new
	// objects on the fly. This could also imply to use a custom Iterator in getObjects() for performance
	// optimization.

	private final String topicName;

	private final String kafkaTopicName;

	private final Class<T> valueClass;

	private final Map<String, T> data = new ConcurrentHashMap<>();

	private final ObjectMapper objectMapper = JsonUtil.newObjectMapper();

	private final KafkaSender sender;

	private final AtomicReference<Runnable> messageReceivedHook = new AtomicReference<>();

	public TopicBasedRepositoryImpl(String kafkaTopicName, String topicName, Class<T> valueClass, KafkaSender sender) {
		// fail-fast for null values
		if (kafkaTopicName == null) {
			throw new IllegalArgumentException("kafkaTopicName must not be null");
		}
		if (topicName == null) {
			throw new IllegalArgumentException("topicName must not be null");
		}
		if (valueClass == null) {
			throw new IllegalArgumentException("valueClass must not be null");
		}
		if (sender == null) {
			throw new IllegalArgumentException("sender must not be null");
		}
		this.kafkaTopicName = kafkaTopicName;
		this.topicName = topicName;
		this.valueClass = valueClass;
		this.sender = sender;
	}

	public final void messageReceived(String topicName, String messageKey, String message) {
		if (!this.kafkaTopicName.equals(topicName)) {
			return;
		}

		Runnable r = messageReceivedHook.get();
		if (r != null) {
			r.run();
		}

		JSONObject obj = new JSONObject(message);
		if (obj.optBoolean("deleted")) {
			data.remove(messageKey);
			return;
		}

		try {
			data.put(messageKey, objectMapper.readValue(obj.getJSONObject("obj").toString(), valueClass));
		} catch (JSONException | IOException e) {
			LoggerFactory.getLogger(getClass()).error("Could not parse object from Kafka message", e);
		}
	}

	@Override
	public final Class<T> getValueClass() {
		return valueClass;
	}

	@Override
	public final String getTopicName() {
		return topicName;
	}

	@Override
	public final boolean containsObject(String id) {
		return data.containsKey(id);
	}

	@Override
	public final Optional<T> getObject(String id) {
		return Optional.ofNullable(data.get(id));
	}

	@Override
	public final Collection<T> getObjects() {
		return Collections.unmodifiableCollection(data.values());
	}

	@Override
	public CompletableFuture<Void> save(T value) {
		try {
			JSONObject message = new JSONObject();
			message.put("obj", new JSONObject(objectMapper.writeValueAsString(value)));
			String key = value.key();
			data.put(key, value);
			return sender.send(kafkaTopicName, key, message.toString());
		}
		catch (JSONException | JsonProcessingException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	@Override
	public CompletableFuture<Void> delete(T value) {
		JSONObject message = new JSONObject();
		message.put("deleted", true);
		String key = value.key();
		data.remove(key);
		return sender.send(kafkaTopicName, key, message.toString());
	}

	@Override
	public CompletableFuture<Void> waitForInitialization(Duration initialWaitTime, Duration idleTime,
		ScheduledExecutorService executorService) {
		CompletableFuture<Void> result = new CompletableFuture<>();

		AtomicReference<ScheduledFuture<?>> idleFuture = new AtomicReference<>();

		Runnable completed = () -> {
			messageReceivedHook.set(null);
			result.complete(null);
		};

		Runnable restartIdleTimer = () -> {
			ScheduledFuture<?> f = idleFuture.get();
			if (f != null) {
				f.cancel(false);
			}
			idleFuture.set(executorService.schedule(completed, idleTime.toMillis(), TimeUnit.MILLISECONDS));
		};

		Runnable r = () -> {
			restartIdleTimer.run();
			messageReceivedHook.set(restartIdleTimer);
		};

		executorService.schedule(r, initialWaitTime.toMillis(), TimeUnit.MILLISECONDS);
		return result;
	}
}
