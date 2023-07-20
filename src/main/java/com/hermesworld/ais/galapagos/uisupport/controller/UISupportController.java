package com.hermesworld.ais.galapagos.uisupport.controller;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.BusinessCapability;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.ccloud.auth.ConfluentCloudAuthenticationModule;
import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationModule;
import com.hermesworld.ais.galapagos.changes.config.GalapagosChangesConfig;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.util.KafkaTopicConfigHelper;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.topics.config.GalapagosTopicConfig;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import com.hermesworld.ais.galapagos.util.CertificateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.time.Period;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Crossfunctional controller for calculating sensible defaults and other useful values mainly for use in UIs. <br>
 * As such, this controller contains some "business logic", e.g. about the combination effects of some Kafka
 * configuration properties.
 *
 * @author AlbrechtFlo
 *
 */
@RestController
@Slf4j
public class UISupportController {

    private final ApplicationsService applicationsService;

    private final TopicService topicService;

    private KafkaClusters kafkaClusters;

    private final NamingService namingService;

    private final GalapagosTopicConfig topicConfig;

    private final GalapagosChangesConfig changesConfig;

    private final CustomLinksConfig customLinksConfig;

    private static final Supplier<ResponseStatusException> badRequest = () -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST);

    private static final Supplier<ResponseStatusException> notFound = () -> new ResponseStatusException(
            HttpStatus.NOT_FOUND);

    private static final int TEN_MEGABYTES = (int) Math.pow(2, 20) * 10;

    private static final int TEN_GIGABYTES = (int) Math.pow(2, 30) * 10;

    private static final long ONE_HOUR = TimeUnit.HOURS.toMillis(1);

    private static final long ONE_WEEK = TimeUnit.DAYS.toMillis(7);

    public UISupportController(ApplicationsService applicationsService, TopicService topicService,
            KafkaClusters kafkaClusters, NamingService namingService, GalapagosTopicConfig topicConfig,
            CustomLinksConfig customLinksConfig, GalapagosChangesConfig changesConfig) {
        this.applicationsService = applicationsService;
        this.topicService = topicService;
        this.kafkaClusters = kafkaClusters;
        this.namingService = namingService;
        this.topicConfig = topicConfig;
        this.customLinksConfig = customLinksConfig;
        this.changesConfig = changesConfig;
    }

    /**
     * Returns all configuration elements of the backend which are also relevant for the frontend. Also includes the
     * custom links to be shown on the dashboard.
     *
     * @return All configuration elements of the backend which are also relevant for the frontend.
     */
    @GetMapping(value = "/api/util/uiconfig", produces = MediaType.APPLICATION_JSON_VALUE)
    public UiConfigDto getUiConfig() {
        UiConfigDto result = new UiConfigDto();
        result.setMinDeprecationTime(toPeriodDto(topicConfig.getMinDeprecationTime()));
        result.setCustomLinks(customLinksConfig.getLinks());
        result.setChangelogEntries(changesConfig.getEntries());
        result.setChangelogMinDays(changesConfig.getMinDays());
        result.setDefaultPicture(changesConfig.getDefaultPicture());
        result.setProfilePicture(changesConfig.getProfilePicture());
        result.setCustomImageUrl(changesConfig.getCustomImageUrl());
        return result;
    }

    @GetMapping(value = "/api/util/customlinks", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CustomLinkConfig> getCustomLinks() {
        return customLinksConfig.getLinks();
    }

    @GetMapping(value = "/api/util/framework-config/{environmentId}/{framework}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getFrameworkConfigTemplate(@PathVariable String environmentId, @PathVariable String framework) {
        switch (framework) {
        case "spring":
            return springConfigBuilder.apply(environmentId);
        case "micronaut":
            return micronautConfigBuilder.apply(environmentId);
        }

        throw notFound.get();
    }

    @PostMapping(value = "/api/util/topic-create-defaults", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TopicCreateDefaultsDto getDefaultTopicCreateParams(@RequestBody QueryTopicCreateDefaultsDto query) {
        TopicCreateDefaultsDto result = new TopicCreateDefaultsDto();
        if (!StringUtils.isEmpty(query.getApplicationId()) && !StringUtils.isEmpty(query.getEnvironmentId())
                && query.getTopicType() != null) {
            result.setTopicNameSuggestion(getTopicNameSuggestion(query));
        }

        result.setDefaultPartitionCount(topicConfig.getDefaultPartitionCount());

        Map<String, String> defaultConfigs = new HashMap<>();
        defaultConfigs.put(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE);
        defaultConfigs.put(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(TimeUnit.DAYS.toMillis(7)));

        if (query.getExpectedMessageCountPerDay() != null && query.getExpectedAvgMessageSizeBytes() != null) {
            int cnt = query.getExpectedMessageCountPerDay();
            long bytesPerDay = cnt * query.getExpectedAvgMessageSizeBytes();
            long segmentBytes = Math.max(TEN_MEGABYTES, bytesPerDay);
            segmentBytes = Math.min(segmentBytes, TEN_GIGABYTES);

            defaultConfigs.put(TopicConfig.SEGMENT_BYTES_CONFIG, String.valueOf(segmentBytes));
        }

        // we set segment.ms to max one week to avoid unexpected effects when e.g. testing retention time settings.
        // If retention is lower, segment.ms will be set to retention time.
        if (query.getRetentionTimeMs() != null) {
            long segmentMs = Math.max(query.getRetentionTimeMs(), ONE_HOUR);
            segmentMs = Math.min(segmentMs, ONE_WEEK);
            defaultConfigs.put(TopicConfig.SEGMENT_MS_CONFIG, String.valueOf(segmentMs));
        }

        result.setDefaultTopicConfigs(defaultConfigs);
        return result;
    }

    @GetMapping(value = "/api/util/default-topic-config/{environmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> getDefaultTopicConfig(@PathVariable String environmentId) {
        KafkaCluster env = kafkaClusters.getEnvironment(environmentId).orElseThrow(notFound);
        try {
            return env.getDefaultTopicConfig().get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Collections.emptyMap();
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e, "Could not query default topic configuration: ");
        }
    }

    @GetMapping(value = "/api/util/supported-kafka-configs", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<KafkaConfigDescriptionDto> getSupportedKafkaConfigs() {
        return KafkaTopicConfigHelper.getConfigKeysAndDescription().entrySet().stream()
                .map(entry -> new KafkaConfigDescriptionDto(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/api/util/environments-for-topic/{topicName:.+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> getEnvironmentsWithTopic(@PathVariable String topicName) {
        return kafkaClusters.getEnvironmentIds().stream()
                .map(envId -> topicService.getTopic(envId, topicName).map(t -> envId).orElse(null))
                .filter(s -> s != null).collect(Collectors.toList());
    }

    @GetMapping(value = "/api/util/supported-devcert-environments", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> getEnvironmentsWithDeveloperCertificateSupport() {
        return kafkaClusters.getEnvironmentIds().stream()
                .map(id -> supportsEnvironmentDeveloperCertificate(id) ? id : null).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/api/util/supported-apikey-environments", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> getEnvironmentsWithDeveloperApikeySupport() {
        return kafkaClusters.getEnvironmentIds().stream().map(id -> supportsEnvironmentDeveloperApiKey(id) ? id : null)
                .filter(Objects::nonNull).collect(Collectors.toList());
    }

    private boolean supportsEnvironmentDeveloperCertificate(String environmentId) {
        return getCertificatesAuthenticationModuleForEnv(environmentId)
                .map(CertificatesAuthenticationModule::supportsDeveloperCertificates).orElse(false);
    }

    private Optional<CertificatesAuthenticationModule> getCertificatesAuthenticationModuleForEnv(String environmentId) {
        CertificatesAuthenticationModule certificateModule;
        Optional<KafkaAuthenticationModule> authModule = kafkaClusters.getAuthenticationModule(environmentId);

        if (authModule.isPresent() && authModule.get() instanceof CertificatesAuthenticationModule) {
            certificateModule = (CertificatesAuthenticationModule) authModule.get();
            return Optional.of(certificateModule);
        }
        return Optional.empty();
    }

    private boolean supportsEnvironmentDeveloperApiKey(String environmentId) {
        return getConfluentAuthenticationModuleForEnv(environmentId)
                .map(ConfluentCloudAuthenticationModule::supportsDeveloperApiKeys).orElse(false);
    }

    private Optional<ConfluentCloudAuthenticationModule> getConfluentAuthenticationModuleForEnv(String environmentId) {
        ConfluentCloudAuthenticationModule confluentModule;
        Optional<KafkaAuthenticationModule> authModule = kafkaClusters.getAuthenticationModule(environmentId);

        if (authModule.isPresent() && authModule.get() instanceof ConfluentCloudAuthenticationModule) {
            confluentModule = (ConfluentCloudAuthenticationModule) authModule.get();
            return Optional.of(confluentModule);
        }
        return Optional.empty();
    }

    @GetMapping(value = "/api/util/common-name/{applicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApplicationCnDto getApplicationCommonName(@PathVariable String applicationId) {
        return applicationsService.getKnownApplication(applicationId)
                .map(app -> new ApplicationCnDto(app.getId(), app.getName(), CertificateUtil.toAppCn(app.getName())))
                .orElseThrow(notFound);
    }

    private String getTopicNameSuggestion(QueryTopicCreateDefaultsDto query) {
        KnownApplication app = applicationsService.getKnownApplication(query.getApplicationId())
                .orElseThrow(badRequest);
        BusinessCapability cap = app.getBusinessCapabilities().stream()
                .filter(bc -> bc.getId().equals(query.getBusinessCapabilityId())).findFirst().orElse(null);

        String name = namingService.getTopicNameSuggestion(query.getTopicType(), app, cap);
        if (name == null) {
            throw badRequest.get();
        }

        return name;
    }

    private ResponseStatusException handleExecutionException(ExecutionException e, String msgPrefix) {
        Throwable t = e.getCause();
        if (t instanceof CertificateException) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, msgPrefix + t.getMessage());
        }
        if (t instanceof NoSuchElementException) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if ((t instanceof IllegalStateException) || (t instanceof IllegalArgumentException)) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, t.getMessage());
        }

        log.error("Unhandled exception in UISupportController", t);
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static String readConfigTemplate(String framework, String authenticationType) throws IOException {
        try (InputStream in = UISupportController.class.getClassLoader()
                .getResourceAsStream("configtemplates/" + framework + "." + authenticationType + ".template.yaml")) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }

    private static PeriodDto toPeriodDto(Period period) {
        return new PeriodDto(period.getYears(), period.getMonths(), period.getDays());
    }

    private final Function<String, String> springConfigBuilder = (environmentId) -> {
        KafkaEnvironmentConfig config = kafkaClusters.getEnvironmentMetadata(environmentId).orElse(null);
        if (config == null) {
            return null;
        }

        try {
            return readConfigTemplate("spring", config.getAuthenticationMode()).replace("${bootstrap.servers}",
                    config.getBootstrapServers());
        }
        catch (IOException e) {
            log.error("Could not read Spring config template", e);
            return null;
        }
    };

    private final Function<String, String> micronautConfigBuilder = (environmentId) -> {
        KafkaEnvironmentConfig config = kafkaClusters.getEnvironmentMetadata(environmentId).orElse(null);
        if (config == null) {
            return null;
        }

        String[] bootstrapServers = config.getBootstrapServers().split(",");

        try {
            String configFile = readConfigTemplate("micronaut", config.getAuthenticationMode());
            StringBuilder sbOut = new StringBuilder();

            String[] lines = configFile.split("\\r?\\n");
            for (String line : lines) {
                if (line.contains("${bootstrap.server}")) {
                    for (String server : bootstrapServers) {
                        sbOut.append(line.replace("${bootstrap.server}", server.trim())).append('\n');
                    }
                }
                else {
                    sbOut.append(line).append('\n');
                }
            }

            return sbOut.toString();
        }
        catch (IOException e) {
            log.error("Could not read Micronaut config template", e);
            return null;
        }
    };

}
