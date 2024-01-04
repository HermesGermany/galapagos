package com.hermesworld.ais.galapagos.kafka.impl;

import com.hermesworld.ais.galapagos.kafka.KafkaExecutorFactory;
import org.apache.kafka.common.KafkaFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Helper class which decouples the completion of {@link KafkaFuture} or {@link CompletableFuture} instances from the
 * main Kafka Thread.
 *
 * @author AlbrechtFlo
 */
public class KafkaFutureDecoupler {

    private final KafkaExecutorFactory executorFactory;

    public KafkaFutureDecoupler(KafkaExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    /**
     * Returns a {@link CompletableFuture} which completes when the given {@link KafkaFuture} completes. If the
     * <code>KafkaFuture</code> is already complete, a completed Future is returned. Otherwise, the returned Future
     * completes on a Thread provided by a fresh <code>ExecutorService</code> of the <code>KafkaExecutorFactory</code>
     * provided for this helper class.
     *
     * @param <T>    Type of the value provided by the Future.
     * @param future Future which may be complete, or which may complete on the Kafka Thread.
     *
     * @return A completable Future which may be already complete if the original Future already was complete, or which
     *         completes on a Thread decoupled from the Kafka Thread.
     */
    public <T> CompletableFuture<T> toCompletableFuture(KafkaFuture<T> future) {
        return decouple(kafkaFutureToCompletableFuture(future));
    }

    /**
     * Returns a {@link CompletableFuture} which completes when the given {@link CompletableFuture} completes. If the
     * <code>ListenableFuture</code> is already complete, a completed Future is returned. Otherwise, the returned Future
     * completes on a Thread provided by a fresh <code>ExecutorService</code> of the <code>KafkaExecutorFactory</code>
     * provided for this helper class.
     *
     * @param <T>               Type of the value provided by the Future.
     * @param completableFuture Future which may be complete, or which may complete on the Kafka Thread.
     *
     * @return A completable Future which may be already complete if the original Future already was complete, or which
     *         completes on a Thread decoupled from the Kafka Thread.
     */
    public <T> CompletableFuture<T> toCompletableFuture(CompletableFuture<T> completableFuture) {
        return decouple(completableFuture);
    }

    private <T> CompletableFuture<T> decouple(CompletableFuture<T> completableFuture) {
        if (completableFuture.isDone()) {
            return completableFuture;
        }

        CompletableFuture<T> result = new CompletableFuture<>();
        ExecutorService executor = executorFactory.newExecutor();

        completableFuture.whenComplete((res, throwable) -> {
            try {
                executor.submit(() -> {
                    if (throwable != null) {
                        result.completeExceptionally(throwable);
                    }
                    else {
                        result.complete(res);
                    }
                });
            }
            finally {
                executor.shutdown();
            }
        });

        return result;
    }

    private <T> CompletableFuture<T> kafkaFutureToCompletableFuture(KafkaFuture<T> future) {
        CompletableFuture<T> result = new CompletableFuture<>();
        future.whenComplete((res, t) -> {
            if (t != null) {
                result.completeExceptionally(t);
            }
            else {
                result.complete(res);
            }
        });

        return result;
    }

}
