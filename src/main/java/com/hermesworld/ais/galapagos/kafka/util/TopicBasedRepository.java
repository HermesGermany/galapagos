package com.hermesworld.ais.galapagos.kafka.util;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

import com.hermesworld.ais.galapagos.util.HasKey;

/**
 * Interface for Repositories (logical data stores) based on a Kafka Topic. Implementations will use the JSON
 * representation of objects stored in the repository to persist the information to Kafka, so chosen repository value
 * types must be serializable as JSON.
 *
 * @param <T> Type of the objects being stored in this repository. The type must implement the {@link HasKey} interface
 *            and be serializable as and deserializable from JSON.
 */
public interface TopicBasedRepository<T extends HasKey> {

	/**
	 * Checks if this repository contains an object with the given ID / key. "Contains" means that at least one record
	 * exists (on the Kafka Topic) representing this object, and the latest record is not marked as "deleted".
	 *
	 * @param id ID of the object, as returned by its {@link HasKey#key()} method.
	 * @return <code>true</code> if such an object exists in this repository, or <code>false</code> otherwise.
	 */
	boolean containsObject(String id);

	/**
	 * Returns an Optional containing the object stored for the given ID in this repository, or an empty Optional
	 * if no such object exists in this repository.
	 *
	 * @param id ID of the object, as returned by its {@link HasKey#key()} method.
	 * @return An Optional, either containing the found object, or an empty Optional.
	 */
	Optional<T> getObject(String id);

	/**
	 * Returns all non-deleted objects currently stored in this repository.
	 *
	 * @return A (possibly empty) collection containing all non-deleted objects currently stored in this repository,
	 * never <code>null</code>.
	 */
	Collection<T> getObjects();

	/**
	 * Stores the given object in this repository. If another object with the same ID (as returned by {@link HasKey#key()})
	 * already exists, it is replaced with the new value. The Kafka Topic is updated accordingly.
	 *
	 * @param value Object to store in this repository.
	 * @return A Completable Future which completes when the repository and the Kafka Topic both have been updated
	 * successfully, or which completes exceptionally if the Kafka Topic could not be updated.
	 */
	CompletableFuture<Void> save(T value);

	/**
	 * Deletes the given object from this repository. The Kafka Topic is updated accordingly. <br>
	 * Deletion is done based on the <i>key</i> returned by the passed value, so no <code>equals()</code> check is done
	 * to lookup the object. Only if no object with such key exists in this repository, this is a no-op.
	 *
	 * @param value Object to delete from this repository.
	 * @return A Completable Future which completes when the repository and the Kafka Topic both have been updated
	 * successfully, or which completes exceptionally if the Kafka Topic could not be updated.
	 */
	CompletableFuture<Void> delete(T value);

	/**
	 * Returns the (short) name of the Kafka topic where the data for this repository is stored in. To get the real
	 * Kafka topic name, you have to prepend the configured prefix for Galapagos Metadata topics.
	 *
	 * @return The (short) name of the Kafka topic where the data for this repository is stored in, never <code>null</code>.
	 */
	String getTopicName();

	/**
	 * Returns the type of objects stored in this repository.
	 *
	 * @return The type of objects stored in this repository.
	 */
	Class<T> getValueClass();

	/**
	 * Waits for the repository to be "initialized", that is, has received initial data from the Kafka Cluster. Due to
	 * the asynchronous, stream-based logic of Kafka data, this can only be determined by specifying two duration values:
	 * First, the time to wait until the first data record will most likely have arrived in the client; second, the
	 * time which will be assumed as "idle time": If no more records arrive in this timeframe, the initialization is
	 * considered "complete" and the returned Future is completed. <br>
	 * Both values have to be selected wisely, as the waiting time for an empty topic will still be
	 * <code>initialWaitTime + idleTime</code>.
	 *
	 * @param initialWaitTime Initial time to wait regardless of any incoming messages.
	 * @param idleTime        Maximum allowed time for no new messages coming in before the initialization is considered
	 *                        "complete".
	 * @param executorService Executor service to schedule waiting tasks on. Also the completion of the Future will occur
	 *                        on a Thread of this executor (<b>never</b> on the calling Thread of this function).
	 * @return A future which completes once the repository is considered "initialized" according to above rules. It
	 * will <b>always</b> complete on a Thread of the given <code>executorService</code>.
	 */
	CompletableFuture<Void> waitForInitialization(Duration initialWaitTime, Duration idleTime,
		ScheduledExecutorService executorService);

}
