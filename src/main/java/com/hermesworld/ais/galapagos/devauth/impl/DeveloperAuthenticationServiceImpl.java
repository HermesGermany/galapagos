package com.hermesworld.ais.galapagos.devauth.impl;

import com.hermesworld.ais.galapagos.devauth.DevAuthenticationMetadata;
import com.hermesworld.ais.galapagos.devauth.DeveloperAuthenticationService;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.auth.CreateAuthenticationResult;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.util.InitPerCluster;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.TimeService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
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

    @Autowired
    public DeveloperAuthenticationServiceImpl(KafkaClusters kafkaClusters, CurrentUserService currentUserService,
            DevUserAclListener aclUpdater, TimeService timeService) {
        this.kafkaClusters = kafkaClusters;
        this.currentUserService = currentUserService;
        this.aclUpdater = aclUpdater;
        this.timeService = timeService;
    }

    @Override
    public void init(KafkaCluster cluster) {
        // TODO delete old metadata topic
        getRepository(cluster).getObjects();
    }

    @Override
    public CompletableFuture<Void> createDeveloperAuthenticationForCurrentUser(String environmentId,
            OutputStream outputStreamForSecret) {
        String userName = currentUserService.getCurrentUserName().orElse(null);
        if (userName == null) {
            return FutureUtil.noUser();
        }

        KafkaCluster cluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        KafkaEnvironmentConfig metadata = kafkaClusters.getEnvironmentMetadata(environmentId).orElse(null);
        if (metadata == null || cluster == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        KafkaAuthenticationModule authModule = kafkaClusters.getAuthenticationModule(environmentId).orElse(null);

        if (authModule == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        TopicBasedRepository<DevAuthenticationMetadata> repository = getRepository(cluster);

        CompletableFuture<Void> removeFuture = repository
                .getObject(
                        userName)
                .map(oldMeta -> aclUpdater.removeAcls(cluster, Collections.singleton(oldMeta))
                        .thenCompose(o -> authModule.deleteDeveloperAuthentication(userName,
                                new JSONObject(oldMeta.getAuthenticationJson()))))
                .orElse(FutureUtil.noop());

        return removeFuture.thenCompose(o -> authModule.createDeveloperAuthentication(userName, new JSONObject()))
                .thenCompose(result -> saveMetadata(cluster, userName, result)).thenApply(result -> {
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

                    return (Void) null;
                })
                .thenCompose(o -> getRepository(cluster).getObject(userName)
                        .map(meta -> aclUpdater.updateAcls(cluster, Collections.singleton(meta)))
                        .orElse(FutureUtil.noop()))
                .thenCompose(o -> clearExpiredDeveloperAuthenticationsOnAllClusters()).thenApply(o -> null);
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
            String authMode = kafkaClusters.getEnvironmentMetadata(cluster.getId())
                    .map(KafkaEnvironmentConfig::getAuthenticationMode).orElse("");

            if (!"certificates".equals(authMode) && !"ccloud".equals(authMode)) {
                continue;
            }
            Set<DevAuthenticationMetadata> expiredDevAuthentications = getRepository(cluster).getObjects().stream()
                    .filter(devAuth -> isExpired(devAuth, authModule)).collect(Collectors.toSet());
            result = result.thenCompose(future -> aclUpdater.removeAcls(cluster, expiredDevAuthentications));
            for (DevAuthenticationMetadata devAuth : expiredDevAuthentications) {
                result = result.thenCompose(o -> getRepository(cluster).delete(devAuth));
                totalClearedDevAuthentications.incrementAndGet();
            }
        }

        return result.thenApply(o -> totalClearedDevAuthentications.get());
    }

    private boolean isExpired(DevAuthenticationMetadata metadata, KafkaAuthenticationModule authModule) {
        JSONObject authJson = new JSONObject(metadata.getAuthenticationJson());
        return authModule.extractExpiryDate(authJson).map(dt -> dt.isBefore(timeService.getTimestamp().toInstant()))
                .orElse(false);
    }

    private CompletableFuture<CreateAuthenticationResult> saveMetadata(KafkaCluster cluster, String userName,
            CreateAuthenticationResult result) {
        return getRepository(cluster).save(toMetadata(userName, result)).thenApply(o -> result);
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
