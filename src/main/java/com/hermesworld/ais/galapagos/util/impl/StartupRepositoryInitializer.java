package com.hermesworld.ais.galapagos.util.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.InitPerCluster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Due to the lazy and asynchronous nature of the topic-based repositories in the service implementations, the
 * implementations can implement {@link InitPerCluster}. If they do, they will be called by this service once the
 * ApplicationContext has started. Their <code>init()</code> method will be called for each known Kafka Cluster, so they
 * can e.g. initialize their repositories.
 *
 * @author AlbrechtFlo
 *
 */
@Component
@Slf4j
public class StartupRepositoryInitializer {

    private final KafkaClusters kafkaClusters;

    private final Duration initialRepositoryLoadWaitTime;

    private final Duration repositoryLoadIdleTime;

    public StartupRepositoryInitializer(KafkaClusters kafkaClusters,
            @Value("${galapagos.initialRepositoryLoadWaitTime:5s}") Duration initialRepositoryLoadWaitTime,
            @Value("${galapagos.repositoryLoadIdleTime:2s}") Duration repositoryLoadIdleTime) {
        this.kafkaClusters = kafkaClusters;
        this.initialRepositoryLoadWaitTime = initialRepositoryLoadWaitTime;
        this.repositoryLoadIdleTime = repositoryLoadIdleTime;
    }

    @EventListener
    public void initializePerCluster(ContextRefreshedEvent event) {
        Collection<InitPerCluster> beans = event.getApplicationContext().getBeansOfType(InitPerCluster.class).values();

        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1);

        List<CompletableFuture<?>> futures = new ArrayList<>();

        log.info("Waiting for Galapagos Metadata repositories to be initialized...");

        try {
            for (String id : kafkaClusters.getEnvironmentIds()) {
                KafkaCluster cluster = kafkaClusters.getEnvironment(id).orElse(null);
                if (cluster != null) {
                    beans.forEach(bean -> bean.init(cluster));
                    cluster.getRepositories().stream().map(r -> r.waitForInitialization(initialRepositoryLoadWaitTime,
                            repositoryLoadIdleTime, executorService)).forEach(futures::add);
                }
            }

            for (CompletableFuture<?> future : futures) {
                try {
                    future.get();
                }
                catch (InterruptedException e) {
                    return;
                }
                catch (ExecutionException e) {
                    log.error("Exception when waiting for Kafka repository initialization", e);
                }
            }
        }
        finally {
            executorService.shutdown();
            try {
                executorService.awaitTermination(1, TimeUnit.MINUTES);
            }
            catch (InterruptedException e) {
                log.warn("Thread has been interrupted while waiting for executor shutdown", e);
            }
        }
    }

}
