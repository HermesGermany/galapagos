package com.hermesworld.ais.galapagos.kafka.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.hermesworld.ais.galapagos.certificates.CaManager;
import com.hermesworld.ais.galapagos.certificates.CertificateService;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaExecutorFactory;
import com.hermesworld.ais.galapagos.kafka.config.impl.KafkaEnvironmentConfigImpl;
import com.hermesworld.ais.galapagos.kafka.impl.ConnectedKafkaClusters;
import lombok.Getter;
import lombok.Setter;
import org.bouncycastle.operator.OperatorException;
import org.bouncycastle.pkcs.PKCSException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

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
    private Resource certificatesWorkdir;

    @Getter
    @Setter
    private boolean readonly;

    @Getter
    @Setter
    private String metadataTopicsPrefix;

    public List<KafkaEnvironmentConfig> getEnvironments() {
        return environments.stream().collect(Collectors.toList());
    }

    @Bean(destroyMethod = "dispose")
    public KafkaClusters kafkaClusters(CertificateService certificateService, KafkaExecutorFactory executorFactory)
            throws IOException, PKCSException, OperatorException, GeneralSecurityException {
        validateConfig();

        File workdir = certificatesWorkdir.getFile();
        workdir.mkdirs();
        if (!workdir.isDirectory()) {
            throw new IllegalArgumentException(
                    "galapagos.kafka.certificates-workdir must point to a directory on the file system! Also check that the current process is allowed to create the directory if it does not exist.");
        }

        Map<String, CaManager> caManagers = new HashMap<>();
        for (KafkaEnvironmentConfigImpl env : environments) {
            caManagers.put(env.getId(), certificateService.buildCaManager(env, workdir));
        }

        certificateService.buildTrustStore(caManagers);

        File fTruststoreFile = new File(workdir, "truststore.p12");
        try (FileOutputStream fos = new FileOutputStream(fTruststoreFile)) {
            fos.write(certificateService.getTrustStorePkcs12());
        }

        return new ConnectedKafkaClusters(environments.stream().collect(Collectors.toList()), caManagers,
                fTruststoreFile, productionEnvironment, metadataTopicsPrefix, executorFactory);
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

        if (!environments.stream().filter(env -> productionEnvironment.equals(env.getId())).findAny().isPresent()) {
            throw new RuntimeException(
                    "No environment configuration given for production environment " + productionEnvironment);
        }

        if (!certificatesWorkdir.isFile()) {
            throw new IllegalArgumentException(
                    "galapagos.kafka.certificates-workdir must point to a directory on the file system!");
        }

    }

}
