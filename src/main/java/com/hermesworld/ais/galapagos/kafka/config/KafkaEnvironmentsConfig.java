package com.hermesworld.ais.galapagos.kafka.config;

import com.hermesworld.ais.galapagos.ccloud.auth.ConfluentCloudAuthenticationModule;
import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaExecutorFactory;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.impl.KafkaEnvironmentConfigImpl;
import com.hermesworld.ais.galapagos.kafka.impl.ConnectedKafkaClusters;
import lombok.Getter;
import lombok.Setter;
import org.bouncycastle.operator.OperatorException;
import org.bouncycastle.pkcs.PKCSException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@ConfigurationProperties(prefix = "galapagos.kafka")
public class KafkaEnvironmentsConfig {

    @Setter
    private List<KafkaEnvironmentConfigImpl> environments = new ArrayList<>();

    @Getter
    @Setter
    private String productionEnvironment;

    @Getter
    @Setter
    private boolean logAdminOperations;

    @Getter
    @Setter
    private Long adminClientRequestTimeout;

    @Getter
    @Setter
    private String metadataTopicsPrefix;

    @Getter
    @Setter
    private List<DefaultAclConfig> defaultAcls;

    public List<KafkaEnvironmentConfig> getEnvironments() {
        return new ArrayList<>(environments);
    }

    @Bean(destroyMethod = "dispose")
    public KafkaClusters kafkaClusters(KafkaExecutorFactory executorFactory,
            @Value("${galapagos.topics.standardReplicationFactor}") int replicationFactor)
            throws IOException, PKCSException, OperatorException, GeneralSecurityException {
        validateConfig();

        Map<String, KafkaAuthenticationModule> authModules = environments.stream()
                .collect(Collectors.toMap(env -> env.getId(), env -> buildAuthenticationModule(env)));

        for (KafkaAuthenticationModule module : authModules.values()) {
            module.init().join();
        }

        return new ConnectedKafkaClusters(new ArrayList<>(environments), authModules, productionEnvironment,
                metadataTopicsPrefix, executorFactory, replicationFactor, logAdminOperations,
                adminClientRequestTimeout);
    }

    private void validateConfig() {
        if (environments.isEmpty()) {
            throw new RuntimeException(
                    "No Kafka environments configured. Please configure at least one Kafka environment using galapagos.kafka.environments[0].<properties>");
        }

        if (productionEnvironment == null) {
            throw new RuntimeException(
                    "No Kafka production environment configured. Please set property galapagos.kafka.production-environment.");
        }

        if (environments.stream().noneMatch(env -> productionEnvironment.equals(env.getId()))) {
            throw new RuntimeException(
                    "No environment configuration given for production environment " + productionEnvironment);
        }
    }

    private KafkaAuthenticationModule buildAuthenticationModule(KafkaEnvironmentConfigImpl envConfig) {
        if ("ccloud".equals(envConfig.getAuthenticationMode())) {
            return new ConfluentCloudAuthenticationModule(envConfig.getCcloud());
        }
        else if ("certificates".equals(envConfig.getAuthenticationMode())) {
            return new CertificatesAuthenticationModule(envConfig.getId(), envConfig.getCertificates());
        }
        throw new IllegalArgumentException("Invalid authentication mode for environment " + envConfig.getId() + ": "
                + envConfig.getAuthenticationMode());
    }

}
