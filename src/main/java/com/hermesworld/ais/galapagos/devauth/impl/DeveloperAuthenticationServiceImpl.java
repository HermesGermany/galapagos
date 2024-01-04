package com.hermesworld.ais.galapagos.devauth.impl;

import com.hermesworld.ais.galapagos.devauth.DevAuthenticationMetadata;
import com.hermesworld.ais.galapagos.devauth.DeveloperAuthenticationService;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.auth.CreateAuthenticationResult;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.util.InitPerCluster;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.TimeService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DeveloperAuthenticationServiceImpl implements DeveloperAuthenticationService, InitPerCluster {

    private final KafkaClusters kafkaClusters;

    private final CurrentUserService currentUserService;

    private final DevUserAclListener aclUpdater;

    private final TimeService timeService;

    public DeveloperAuthenticationServiceImpl(KafkaClusters kafkaClusters, CurrentUserService currentUserService,
            DevUserAclListener aclUpdater, TimeService timeService) {
        this.kafkaClusters = kafkaClusters;
        this.currentUserService = currentUserService;
        this.aclUpdater = aclUpdater;
        this.timeService = timeService;
    }

    @Override
    public void init(KafkaCluster cluster) {
        getRepository(cluster).getObjects();
    }

    @Override
    public CompletableFuture<DevAuthenticationMetadata> createDeveloperAuthenticationForCurrentUser(
            String environmentId, OutputStream outputStreamForSecret) {
        String userName = currentUserService.getCurrentUserName().orElse(null);
        if (userName == null) {
            return FutureUtil.noUser();
        }

        KafkaCluster cluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (cluster == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        KafkaAuthenticationModule authModule = kafkaClusters.getAuthenticationModule(environmentId).orElse(null);

        if (authModule == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        TopicBasedRepository<DevAuthenticationMetadata> repository = getRepository(cluster);

        CompletableFuture<Void> removeFuture = repository.getObject(userName)
                .map(oldMeta -> aclUpdater.removeAcls(cluster, Set.of(oldMeta)).thenCompose(o -> authModule
                        .deleteDeveloperAuthentication(userName, new JSONObject(oldMeta.getAuthenticationJson()))))
                .orElse(FutureUtil.noop());

        return removeFuture.thenCompose(o -> authModule.createDeveloperAuthentication(userName, new JSONObject()))
                .thenCompose(result -> saveMetadata(cluster, userName, result).thenApply(meta -> {
                    byte[] secretData = result.getPrivateAuthenticationData();
                    if (secretData == null) {
                        log.error("No secret data for developer authentication returned by generation");
                        return null;
                    }
                    try {
                        outputStreamForSecret.write(secretData);
                    }
                    catch (IOException e) {
                        log.warn("Could not write secret data of developer authentication to output stream", e);
                    }

                    return meta;
                }).thenCompose(meta -> meta == null
                        ? CompletableFuture.failedFuture(new NoSuchElementException("No authentication received"))
                        : aclUpdater.updateAcls(cluster, Set.of(meta))
                                .thenCompose(o -> clearExpiredDeveloperAuthenticationsOnAllClusters())
                                .thenApply(o -> meta)));
    }

    @Override
    public Optional<DevAuthenticationMetadata> getDeveloperAuthenticationOfCurrentUser(String environmentId) {
        String userName = currentUserService.getCurrentUserName().orElse(null);
        KafkaCluster cluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (userName == null || cluster == null) {
            return Optional.empty();
        }
        KafkaAuthenticationModule authModule = kafkaClusters.getAuthenticationModule(environmentId).orElse(null);

        if (authModule == null) {
            return Optional.empty();
        }

        DevAuthenticationMetadata metadata = getRepository(cluster).getObject(userName).orElse(null);
        if (metadata == null || isExpired(metadata, authModule)) {
            return Optional.empty();
        }

        return Optional.of(metadata);
    }

    @Override
    public CompletableFuture<Integer> clearExpiredDeveloperAuthenticationsOnAllClusters() {
        CompletableFuture<Void> result = FutureUtil.noop();
        AtomicInteger totalClearedDevAuthentications = new AtomicInteger();

        for (KafkaCluster cluster : kafkaClusters.getEnvironments()) {
            KafkaAuthenticationModule authModule = kafkaClusters.getAuthenticationModule(cluster.getId()).orElse(null);

            if (authModule == null) {
                return FutureUtil.noSuchEnvironment(cluster.getId());
            }
            Set<DevAuthenticationMetadata> expiredDevAuthentications = getRepository(cluster).getObjects().stream()
                    .filter(devAuth -> isExpired(devAuth, authModule)).collect(Collectors.toSet());
            result = result.thenCompose(o -> aclUpdater.removeAcls(cluster, expiredDevAuthentications));
            for (DevAuthenticationMetadata devAuth : expiredDevAuthentications) {
                JSONObject authJson = new JSONObject(devAuth.getAuthenticationJson());
                result = result
                        .thenCompose(o -> authModule.deleteDeveloperAuthentication(devAuth.getUserName(), authJson))
                        .thenCompose(o -> getRepository(cluster).delete(devAuth));
                totalClearedDevAuthentications.incrementAndGet();
            }
        }

        return result.thenApply(o -> totalClearedDevAuthentications.get());
    }

    @Override
    public List<DevAuthenticationMetadata> getAllDeveloperAuthentications(String environmentId) {
        KafkaCluster cluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (cluster == null) {
            return List.of();
        }
        KafkaAuthenticationModule authModule = kafkaClusters.getAuthenticationModule(cluster.getId()).orElseThrow();

        return getRepository(cluster).getObjects().stream().filter(metadata -> !isExpired(metadata, authModule))
                .collect(Collectors.toList());
    }

    private boolean isExpired(DevAuthenticationMetadata metadata, KafkaAuthenticationModule authModule) {
        JSONObject authJson = new JSONObject(metadata.getAuthenticationJson());
        return authModule.extractExpiryDate(authJson).map(dt -> dt.isBefore(timeService.getTimestamp().toInstant()))
                .orElse(false);
    }

    private CompletableFuture<DevAuthenticationMetadata> saveMetadata(KafkaCluster cluster, String userName,
            CreateAuthenticationResult result) {
        DevAuthenticationMetadata metadata = toMetadata(userName, result);
        return getRepository(cluster).save(toMetadata(userName, result)).thenApply(o -> metadata);
    }

    static TopicBasedRepository<DevAuthenticationMetadata> getRepository(KafkaCluster cluster) {
        return cluster.getRepository("devauth", DevAuthenticationMetadata.class);
    }

    private DevAuthenticationMetadata toMetadata(String userName, CreateAuthenticationResult result) {
        DevAuthenticationMetadata metadata = new DevAuthenticationMetadata();
        metadata.setUserName(userName);
        metadata.setAuthenticationJson(result.getPublicAuthenticationData().toString());

        return metadata;
    }

}
