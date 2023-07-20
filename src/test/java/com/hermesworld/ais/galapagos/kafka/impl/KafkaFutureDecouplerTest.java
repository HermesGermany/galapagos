package com.hermesworld.ais.galapagos.kafka.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.util.concurrent.FailureCallback;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.util.concurrent.SuccessCallback;

import com.hermesworld.ais.galapagos.kafka.KafkaExecutorFactory;

class KafkaFutureDecouplerTest {

    private static ThreadFactory tfAdminClient = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "admin-client-" + System.currentTimeMillis());
        }
    };

    private static ThreadFactory tfDecoupled = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "decoupled-" + System.currentTimeMillis());
        }
    };

    private static KafkaExecutorFactory executorFactory = () -> {
        return Executors.newSingleThreadExecutor(tfDecoupled);
    };

    private AdminClientStub adminClient;

    @BeforeEach
    void initAdminClient() {
        adminClient = new AdminClientStub();
        adminClient.setKafkaThreadFactory(tfAdminClient);
    }

    @AfterEach
    void closeAdminClient() {
        adminClient.close();
    }

    @Test
    void testDecoupling_kafkaFuture() throws Exception {
        // first, test that the futures usually would complete on our Threads
        AtomicBoolean onAdminClientThread = new AtomicBoolean();
        adminClient.describeCluster().nodes().thenApply(c -> {
            onAdminClientThread.set(Thread.currentThread().getName().startsWith("admin-client-"));
            return null;
        }).get();

        assertTrue(onAdminClientThread.get());

        // after decoupling, future should complete on another Thread
        KafkaFutureDecoupler decoupler = new KafkaFutureDecoupler(executorFactory);

        onAdminClientThread.set(false);
        decoupler.toCompletableFuture(adminClient.describeCluster().nodes()).thenCompose(o -> {
            onAdminClientThread.set(Thread.currentThread().getName().startsWith("admin-client-"));
            return CompletableFuture.completedFuture(null);
        }).get();

        assertFalse(onAdminClientThread.get());
    }

    @Test
    void testDecoupling_concatenation() throws Exception {
        List<String> threadNames = new ArrayList<String>();

        KafkaFutureDecoupler decoupler = new KafkaFutureDecoupler(executorFactory);

        decoupler.toCompletableFuture(adminClient.describeCluster().nodes()).thenCompose(o -> {
            threadNames.add(Thread.currentThread().getName());
            return decoupler.toCompletableFuture(adminClient
                    .createAcls(List.of(new AclBinding(
                            new ResourcePattern(ResourceType.TOPIC, "test", PatternType.LITERAL),
                            new AccessControlEntry("testuser", "*", AclOperation.ALL, AclPermissionType.ALLOW))))
                    .all());
        }).thenApply(o -> {
            threadNames.add(Thread.currentThread().getName());
            return null;
        }).get();

        for (String tn : threadNames) {
            assertTrue(tn.startsWith("decoupled-"));
        }

        // must be two different Threads!
        assertEquals(2, new HashSet<>(threadNames).size());
    }

    @Test
    void testDecoupling_listenableFuture() throws Exception {
        AtomicBoolean onAdminClientThread = new AtomicBoolean();

        // after decoupling, future should complete on another Thread
        KafkaFutureDecoupler decoupler = new KafkaFutureDecoupler(executorFactory);

        ListenableFuture<?> future = new KafkaFutureListenableAdapter<>(adminClient.describeCluster().nodes());

        decoupler.toCompletableFuture(future).thenCompose(o -> {
            onAdminClientThread.set(Thread.currentThread().getName().startsWith("admin-client-"));
            return CompletableFuture.completedFuture(null);
        }).get();

        assertFalse(onAdminClientThread.get());
    }

    @Test
    void testDecoupling_doneFuture() throws Exception {
        AtomicInteger factoryInvocations = new AtomicInteger();

        KafkaExecutorFactory countingExecutorFactory = () -> {
            factoryInvocations.incrementAndGet();
            return Executors.newSingleThreadExecutor(tfDecoupled);
        };

        KafkaFutureDecoupler decoupler = new KafkaFutureDecoupler(countingExecutorFactory);

        KafkaFuture<?> future = adminClient.describeCluster().nodes();
        future.get();

        AtomicBoolean applyInvoked = new AtomicBoolean();
        decoupler.toCompletableFuture(future).thenApply(o -> applyInvoked.getAndSet(true)).get();

        assertTrue(applyInvoked.get());
        assertEquals(0, factoryInvocations.get());
    }

    @Test
    void testDecoupling_failingFuture() throws Exception {
        AtomicInteger factoryInvocations = new AtomicInteger();

        KafkaExecutorFactory countingExecutorFactory = () -> {
            factoryInvocations.incrementAndGet();
            return Executors.newSingleThreadExecutor(tfDecoupled);
        };

        KafkaFutureDecoupler decoupler = new KafkaFutureDecoupler(countingExecutorFactory);

        AtomicBoolean onAdminClientThread = new AtomicBoolean();

        adminClient.setFailOnDescribeCluster(true);
        KafkaFuture<?> future = adminClient.describeCluster().nodes();
        try {
            decoupler.toCompletableFuture(future).whenComplete((t, ex) -> {
                onAdminClientThread.set(Thread.currentThread().getName().startsWith("admin-client-"));
            }).get();
            fail("Decoupled future should have failed");
        }
        catch (ExecutionException e) {
            assertEquals(1, factoryInvocations.get());
            assertTrue(e.getCause() instanceof KafkaException);
        }
    }

    @Test
    void testDecoupling_failedFuture_direct() throws Exception {
        AtomicInteger factoryInvocations = new AtomicInteger();

        KafkaExecutorFactory countingExecutorFactory = () -> {
            factoryInvocations.incrementAndGet();
            return Executors.newSingleThreadExecutor(tfDecoupled);
        };

        KafkaFutureDecoupler decoupler = new KafkaFutureDecoupler(countingExecutorFactory);

        adminClient.setFailOnDescribeCluster(true);
        KafkaFuture<?> future = adminClient.describeCluster().nodes();
        try {
            future.get();
            fail("Future should have failed");
        }
        catch (ExecutionException e) {
            // OK, Future is failed now
        }

        try {
            decoupler.toCompletableFuture(future).get();
            fail("Decoupled future should have failed");
        }
        catch (ExecutionException e) {
            assertEquals(0, factoryInvocations.get());
            assertTrue(e.getCause() instanceof KafkaException);
        }
    }

    private static class KafkaFutureListenableAdapter<T> implements ListenableFuture<T> {

        private KafkaFuture<T> adaptee;

        public KafkaFutureListenableAdapter(KafkaFuture<T> adaptee) {
            this.adaptee = adaptee;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return adaptee.cancel(mayInterruptIfRunning);
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return adaptee.get();
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return adaptee.get(timeout, unit);
        }

        @Override
        public boolean isCancelled() {
            return adaptee.isCancelled();
        }

        @Override
        public boolean isDone() {
            return adaptee.isDone();
        }

        @Override
        public void addCallback(ListenableFutureCallback<? super T> callback) {
            adaptee.whenComplete((t, ex) -> {
                if (ex != null) {
                    callback.onFailure(ex);
                }
                else {
                    callback.onSuccess(t);
                }
            });
        }

        @Override
        public void addCallback(SuccessCallback<? super T> successCallback, FailureCallback failureCallback) {
            adaptee.whenComplete((t, ex) -> {
                if (ex != null) {
                    failureCallback.onFailure(ex);
                }
                else {
                    successCallback.onSuccess(t);
                }
            });
        }
    }

}
