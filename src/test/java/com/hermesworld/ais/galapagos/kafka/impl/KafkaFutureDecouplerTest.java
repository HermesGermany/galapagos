package com.hermesworld.ais.galapagos.kafka.impl;

import com.hermesworld.ais.galapagos.kafka.KafkaExecutorFactory;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class KafkaFutureDecouplerTest {

    private static final ThreadFactory tfAdminClient = r -> new Thread(r, "admin-client-" + System.currentTimeMillis());

    private static final ThreadFactory tfDecoupled = r -> new Thread(r, "decoupled-" + System.currentTimeMillis());

    private static final KafkaExecutorFactory adminClientExecutorFactory = () -> Executors
            .newSingleThreadExecutor(tfAdminClient);

    private static final KafkaExecutorFactory executorFactory = () -> Executors.newSingleThreadExecutor(tfDecoupled);

    private AdminClientStub adminClient;

    @BeforeEach
    void initAdminClient() {
        adminClient = new AdminClientStub();
        adminClient.setKafkaThreadFactory(tfAdminClient);
    }

    @Test
    void testDecoupling_kafkaFuture() throws Exception {
        // first, test that the futures usually would complete on our Threads
        AtomicBoolean onAdminClientThread = new AtomicBoolean();
        adminClient.describeCluster().thenApply(c -> {
            onAdminClientThread.set(Thread.currentThread().getName().startsWith("admin-client-"));
            return null;
        }).get();

        assertTrue(onAdminClientThread.get());

        // after decoupling, future should complete on another Thread
        KafkaFutureDecoupler decoupler = new KafkaFutureDecoupler(executorFactory);

        onAdminClientThread.set(false);
        decoupler.toCompletableFuture(adminClient.describeCluster()).thenCompose(o -> {
            onAdminClientThread.set(Thread.currentThread().getName().startsWith("admin-client-"));
            return CompletableFuture.completedFuture(null);
        }).get();

        assertFalse(onAdminClientThread.get());
    }

    @Test
    void testDecoupling_completableFuture() throws Exception {
        AtomicBoolean onAdminClientThread = new AtomicBoolean();

        // after decoupling, future should complete on another Thread
        KafkaFutureDecoupler decoupler = new KafkaFutureDecoupler(executorFactory);

        CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(200);
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, adminClientExecutorFactory.newExecutor());

        decoupler.toCompletableFuture(future).thenCompose(o -> {
            onAdminClientThread.set(Thread.currentThread().getName().startsWith("admin-client-"));
            return CompletableFuture.completedFuture(null);
        }).get();

        assertFalse(onAdminClientThread.get(), "Future was not decoupled; completion stage ran on admin client Thread");
    }

    @Test
    void testDecoupling_concatenation() throws Exception {
        List<String> threadNames = new ArrayList<String>();

        KafkaFutureDecoupler decoupler = new KafkaFutureDecoupler(executorFactory);

        decoupler.toCompletableFuture(adminClient.describeCluster()).thenCompose(o -> {
            threadNames.add(Thread.currentThread().getName());
            return decoupler.toCompletableFuture(adminClient.createAcls(
                    List.of(new AclBinding(new ResourcePattern(ResourceType.TOPIC, "test", PatternType.LITERAL),
                            new AccessControlEntry("testuser", "*", AclOperation.ALL, AclPermissionType.ALLOW)))));
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
    void testDecoupling_doneFuture() throws Exception {
        AtomicInteger factoryInvocations = new AtomicInteger();

        KafkaExecutorFactory countingExecutorFactory = () -> {
            factoryInvocations.incrementAndGet();
            return Executors.newSingleThreadExecutor(tfDecoupled);
        };

        KafkaFutureDecoupler decoupler = new KafkaFutureDecoupler(countingExecutorFactory);

        KafkaFuture<?> future = adminClient.describeCluster();
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

        adminClient.setFailOnDescribeCluster(true);
        KafkaFuture<?> future = adminClient.describeCluster();
        try {
            decoupler.toCompletableFuture(future).whenComplete((t, ex) -> {
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
        KafkaFuture<?> future = adminClient.describeCluster();
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

}
