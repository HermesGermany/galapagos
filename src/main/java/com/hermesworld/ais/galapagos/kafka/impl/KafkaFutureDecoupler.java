package com.hermesworld.ais.galapagos.kafka.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.apache.kafka.common.KafkaFuture;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import com.hermesworld.ais.galapagos.kafka.KafkaExecutorFactory;

/**
 * Helper class which decouples the completion of {@link KafkaFuture} or {@link ListenableFuture} instances from the
 * main Kafka Thread.
 *
 * @author AlbrechtFlo
 *
 */
public class KafkaFutureDecoupler {

    private KafkaExecutorFactory executorFactory;

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
        return toCompletableFuture(future, cb -> future.whenComplete((t, ex) -> {
            if (ex != null) {
                cb.onFailure(ex);
            }
            else {
                cb.onSuccess(t);
            }
        }));
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
        return toCompletableFuture(completableFuture, cb -> completableFuture.whenComplete((t, ex) -> {
            if (ex != null) {
                cb.onFailure(ex);
            }
            else {
                cb.onSuccess(t);
            }
        }));
    }

    /**
     * Returns a {@link CompletableFuture} which completes when the given {@link ListenableFuture} completes. If the
     * <code>ListenableFuture</code> is already complete, a completed Future is returned. Otherwise, the returned Future
     * completes on a Thread provided by a fresh <code>ExecutorService</code> of the <code>KafkaExecutorFactory</code>
     * provided for this helper class.
     *
     * @param <T>    Type of the value provided by the Future.
     * @param future Future which may be complete, or which may complete on the Kafka Thread.
     *
     * @return A completable Future which may be already complete if the original Future already was complete, or which
     *         completes on a Thread decoupled from the Kafka Thread.
     */
    public <T> CompletableFuture<T> toCompletableFuture(ListenableFuture<T> future) {
        return toCompletableFuture(future, cb -> future.addCallback(cb));
    }

    private <T> CompletableFuture<T> toCompletableFuture(Future<T> simpleFuture,
            Consumer<ListenableFutureCallback<T>> callbackHookConsumer) {
        if (simpleFuture.isDone()) {
            try {
                return CompletableFuture.completedFuture(simpleFuture.get());
            }
            catch (ExecutionException e) {
                return CompletableFuture.failedFuture(e.getCause());
            }
            catch (InterruptedException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        // this assumes that the associated operation of the KafkaFuture has already been scheduled to the KafkaAdmin
        // thread
        CompletableFuture<T> result = new CompletableFuture<T>();
        ExecutorService executor = executorFactory.newExecutor();

        ListenableFutureCallback<T> callback = new ListenableFutureCallback<T>() {
            @Override
            public void onFailure(Throwable ex) {
                try {
                    executor.submit(() -> result.completeExceptionally(ex));
                }
                finally {
                    executor.shutdown();
                }
            }

            @Override
            public void onSuccess(T t) {
                try {
                    executor.submit(() -> result.complete(t));
                }
                finally {
                    executor.shutdown();
                }
            }
        };

        // hook the callback into the original Future. This depends on the Future implementation (Kafka or Listenable).
        callbackHookConsumer.accept(callback);

        return result;
    }

}
