package com.hermesworld.ais.galapagos.kafka;

import java.util.concurrent.ExecutorService;

/**
 * Interface of a factory for Executors which will be used for decoupling Kafka calls from the Kafka Thread. This is
 * required for proper concatenation of <code>CompletableFuture</code>s, as otherwise, a second Kafka invocation after a
 * first one could cause a deadlock within the Kafka Thread. <br>
 * One Executor is created (and immediately shut down) for each Kafka Action which must be decoupled. <br>
 * Implementations of this interface can e.g. provide special executors which deal with Thread-local static accessor
 * classes like Spring Security's <code>SecurityContextHolder</code>.
 *
 * @author AlbrechtFlo
 *
 */
public interface KafkaExecutorFactory {

    /**
     * Creates a new executor service for decoupling a single Kafka Task from the Kafka Thread. The executor is
     * relatively short-lived, and it is okay to provide only one Thread, as no parallel execution occurs on this
     * executor.
     *
     * @return A new executor, never <code>null</code>.
     */
    ExecutorService newExecutor();

}
