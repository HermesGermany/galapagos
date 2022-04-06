package com.hermesworld.ais.galapagos.tooling.impl;

import com.hermesworld.ais.galapagos.adminjobs.impl.ToolingUser;
import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.applications.impl.KnownApplicationImpl;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.auth.CreateAuthenticationResult;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentsConfig;
import com.hermesworld.ais.galapagos.kafka.util.AclSupport;
import com.hermesworld.ais.galapagos.kafka.util.InitPerCluster;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.naming.ApplicationPrefixes;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.tooling.ToolingAuthenticationMetadata;
import com.hermesworld.ais.galapagos.tooling.ToolingAuthenticationService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
@Slf4j
public class ToolingAuthenticationServiceImpl implements ToolingAuthenticationService, InitPerCluster {

    private final KafkaClusters kafkaClusters;

    private final CurrentUserService currentUserService;

    private final ApplicationsService applicationsService;

    private final AclSupport aclSupport;

    private final NamingService namingService;

    private final KafkaEnvironmentsConfig kafkaConfig;

    @Autowired
    public ToolingAuthenticationServiceImpl(KafkaClusters kafkaClusters, CurrentUserService currentUserService,
            ApplicationsService applicationsService, AclSupport aclSupport, NamingService namingService,
            KafkaEnvironmentsConfig kafkaConfig) {
        this.kafkaClusters = kafkaClusters;
        this.currentUserService = currentUserService;
        this.applicationsService = applicationsService;
        this.aclSupport = aclSupport;
        this.namingService = namingService;
        this.kafkaConfig = kafkaConfig;
    }

    @Override
    public void init(KafkaCluster cluster) {
        getRepository(cluster).getObjects();
    }

    @Override
    public Optional<ToolingAuthenticationMetadata> getToolingAuthentication(String environmentId) {
        String userName = currentUserService.getCurrentUserName().orElse(null);
        KafkaCluster cluster = kafkaClusters.getEnvironment(environmentId).orElse(null);
        if (userName == null || cluster == null) {
            return Optional.empty();
        }
        KafkaAuthenticationModule authModule = kafkaClusters.getAuthenticationModule(environmentId).orElse(null);

        if (authModule == null) {
            return Optional.empty();
        }

        ToolingAuthenticationMetadata metadata = getRepository(cluster).getObject(userName).orElse(null);
        if (metadata == null) {
            return Optional.empty();
        }

        return Optional.of(metadata);
    }

    @Override
    public CompletableFuture<ToolingAuthenticationMetadata> createToolingAuthentication(String environmentId,
            OutputStream outputStreamForSecret, String applicationName)
            throws ExecutionException, InterruptedException {
        ApplicationMetadata toolMetadata = new ApplicationMetadata();
        KafkaCluster cluster = kafkaClusters.getEnvironment(environmentId).orElse(null);

        if (cluster == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        KafkaAuthenticationModule authModule = kafkaClusters.getAuthenticationModule(environmentId).orElse(null);

        if (authModule == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        return authModule.createToolingAuthentication(applicationName, new JSONObject())
                .thenCompose(result -> saveMetadata(cluster, applicationName, result).thenApply(meta -> {
                    byte[] secretData = result.getPrivateAuthenticationData();
                    if (secretData == null) {
                        log.error("No secret data for tooling authentication returned by generation");
                        return null;
                    }
                    try {
                        outputStreamForSecret.write(secretData);
                    }
                    catch (IOException e) {
                        log.warn("Could not write secret data of tooling authentication to output stream", e);
                    }

                    toolMetadata.setApplicationId("galapagos_tooling");
                    toolMetadata.setAuthenticationJson(result.getPublicAuthenticationData().toString());
                    KnownApplication dummyApp = new KnownApplicationImpl("galapagos", "Galapagos");

                    ApplicationPrefixes prefixes = namingService.getAllowedPrefixes(dummyApp);

                    // intentionally use config value here - could differ e.g. for Galapagos test instance on same
                    // cluster
                    toolMetadata.setInternalTopicPrefixes(List.of(kafkaConfig.getMetadataTopicsPrefix()));
                    toolMetadata.setConsumerGroupPrefixes(prefixes.getConsumerGroupPrefixes());
                    toolMetadata.setTransactionIdPrefixes(prefixes.getTransactionIdPrefixes());

                    return meta;
                }).thenCompose(meta -> cluster
                        .updateUserAcls(new ToolingUser(toolMetadata, cluster.getId(), authModule, aclSupport))
                        .thenApply(o -> meta)));
    }

    @Override
    public CompletableFuture<Void> deleteToolingAuthentication(String environmentId) {
        KafkaCluster cluster = kafkaClusters.getEnvironment(environmentId).orElse(null);

        if (cluster == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        KafkaAuthenticationModule authModule = kafkaClusters.getAuthenticationModule(environmentId).orElse(null);
        if (authModule == null) {
            return FutureUtil.noSuchEnvironment(environmentId);
        }

        if (getToolingAuthentication(environmentId).isEmpty()) {
            return CompletableFuture.failedFuture(new NoSuchElementException(
                    "No tooling authentication found on environment with ID " + environmentId));
        }

        ToolingAuthenticationMetadata tooling = getToolingAuthentication(environmentId).get();

        return authModule.deleteToolingAuthentication(new JSONObject(tooling.getAuthenticationJson()))
                .thenApply(f -> null);
    }

    private TopicBasedRepository<ToolingAuthenticationMetadata> getRepository(KafkaCluster cluster) {
        return cluster.getRepository("tooling", ToolingAuthenticationMetadata.class);
    }

    private CompletableFuture<ToolingAuthenticationMetadata> saveMetadata(KafkaCluster cluster, String applicationName,
            CreateAuthenticationResult result) {
        ToolingAuthenticationMetadata metadata = toMetadata(applicationName, result);
        return getRepository(cluster).save(toMetadata(applicationName, result)).thenApply(o -> metadata);
    }

    private ToolingAuthenticationMetadata toMetadata(String applicationName, CreateAuthenticationResult result) {
        ToolingAuthenticationMetadata metadata = new ToolingAuthenticationMetadata();
        metadata.setApplicationName(applicationName);
        metadata.setAuthenticationJson(result.getPublicAuthenticationData().toString());

        return metadata;
    }

}
