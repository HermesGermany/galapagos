package com.hermesworld.ais.galapagos.devcerts.impl;

import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationModule;
import com.hermesworld.ais.galapagos.certificates.impl.CertificateSignResult;
import com.hermesworld.ais.galapagos.devcerts.DevCertificateMetadata;
import com.hermesworld.ais.galapagos.devcerts.DeveloperCertificateService;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.util.InitPerCluster;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.TimeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DeveloperCertificateServiceImpl implements DeveloperCertificateService, InitPerCluster {

    // TODO Change to a "Developer Authentication Service"

    private final KafkaClusters kafkaClusters;

    private final CurrentUserService currentUserService;

    private final DevUserAclListener aclUpdater;

    private final TimeService timeService;

    @Autowired
    public DeveloperCertificateServiceImpl(KafkaClusters kafkaClusters, CurrentUserService currentUserService,
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
    public CompletableFuture<Void> createDeveloperCertificateForCurrentUser(String environmentId,
            OutputStream p12OutputStream) {
        String userName = currentUserService.getCurrentUserName().orElse(null);
        if (userName == null) {
            return FutureUtil.noUser();
        }

        KafkaCluster cluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        KafkaEnvironmentConfig metadata = kafkaClusters.getEnvironmentMetadata(environmentId).orElse(null);
        if (metadata == null || cluster == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        if (!"certificates".equals(metadata.getAuthenticationMode())) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Environment " + environmentId + " does not use Certificates for authentication."));
        }

        CertificatesAuthenticationModule authModule = (CertificatesAuthenticationModule) kafkaClusters
                .getAuthenticationModule(environmentId).orElse(null);

        if (authModule == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        TopicBasedRepository<DevCertificateMetadata> repository = getRepository(cluster);

        CompletableFuture<Void> removeFuture = repository.getObject(userName)
                .map(oldMeta -> aclUpdater.removeAcls(cluster, Collections.singleton(oldMeta)))
                .orElse(FutureUtil.noop());

        return removeFuture.thenCompose(o -> authModule.createDeveloperCertificateAndPrivateKey(userName))
                .thenCompose(result -> saveMetadata(cluster, userName, result)).thenApply(result -> {
                    byte[] p12Data = result.getP12Data().orElse(null);
                    if (p12Data == null) {
                        log.error("No PKCS data for developer certificate returned by generation");
                        return null;
                    }
                    try {
                        p12OutputStream.write(p12Data);
                    }
                    catch (IOException e) {
                        log.warn("Could not write PKCS data of developer certificate to output stream", e);
                    }
                    return (Void) null;
                }).thenCompose(o -> clearExpiredDeveloperCertificatesOnAllClusters())
                .thenCompose(o -> getRepository(cluster).getObject(userName)
                        .map(meta -> aclUpdater.updateAcls(cluster, Collections.singleton(meta)))
                        .orElse(FutureUtil.noop()));
    }

    @Override
    public Optional<DevCertificateMetadata> getDeveloperCertificateOfCurrentUser(String environmentId) {
        String userName = currentUserService.getCurrentUserName().orElse(null);
        KafkaCluster cluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (userName == null || cluster == null) {
            return Optional.empty();
        }

        DevCertificateMetadata metadata = getRepository(cluster).getObject(userName).orElse(null);
        if (metadata == null || metadata.getExpiryDate().isBefore(timeService.getTimestamp().toInstant())) {
            return Optional.empty();
        }

        return Optional.of(metadata);
    }

    @Override
    public CompletableFuture<Integer> clearExpiredDeveloperCertificatesOnAllClusters() {
        CompletableFuture<Void> result = FutureUtil.noop();
        AtomicInteger totalClearedDevCerts = new AtomicInteger();

        for (KafkaCluster cluster : kafkaClusters.getEnvironments()) {
            String authMode = kafkaClusters.getEnvironmentMetadata(cluster.getId())
                    .map(KafkaEnvironmentConfig::getAuthenticationMode).orElse("");

            if (!"certificates".equals(authMode)) {
                continue;
            }
            Set<DevCertificateMetadata> expiredDevCerts = getRepository(cluster).getObjects().stream()
                    .filter(devCert -> devCert.getExpiryDate().isBefore(timeService.getTimestamp().toInstant()))
                    .collect(Collectors.toSet());
            result = result.thenCompose(future -> aclUpdater.removeAcls(cluster, expiredDevCerts));
            for (DevCertificateMetadata cert : expiredDevCerts) {
                result = result.thenCompose(o -> getRepository(cluster).delete(cert));
                totalClearedDevCerts.incrementAndGet();
            }
        }

        return result.thenApply(o -> totalClearedDevCerts.get());
    }

    private CompletableFuture<CertificateSignResult> saveMetadata(KafkaCluster cluster, String userName,
            CertificateSignResult result) {
        return getRepository(cluster).save(toMetadata(userName, result)).thenApply(o -> result);
    }

    static TopicBasedRepository<DevCertificateMetadata> getRepository(KafkaCluster cluster) {
        return cluster.getRepository("devcerts", DevCertificateMetadata.class);
    }

    private DevCertificateMetadata toMetadata(String userName, CertificateSignResult result) {
        DevCertificateMetadata metadata = new DevCertificateMetadata();
        metadata.setUserName(userName);
        metadata.setCertificateDn(result.getDn());
        metadata.setExpiryDate(Instant.ofEpochMilli(result.getCertificate().getNotAfter().getTime()));
        return metadata;
    }

}
