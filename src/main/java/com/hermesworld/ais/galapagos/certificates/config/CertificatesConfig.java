package com.hermesworld.ais.galapagos.certificates.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@ConfigurationProperties("galapagos.certificates")
@Configuration
public class CertificatesConfig {

    @Getter
    @Setter
    private Resource certificatesWorkdir;

//    File workdir = certificatesWorkdir.getFile();
//        workdir.mkdirs();
//        if (!workdir.isDirectory()) {
//        throw new IllegalArgumentException(
//                "galapagos.kafka.certificates-workdir must point to a directory on the file system! Also check that the current process is allowed to create the directory if it does not exist.");
//    }
//
//    Map<String, CaManager> caManagers = new HashMap<>();
//        for (
//    KafkaEnvironmentConfigImpl env : environments) {
//        caManagers.put(env.getId(), certificateService.buildCaManager(env, workdir));
//    }
//
//        certificateService.buildTrustStore(caManagers);
//
//    File fTruststoreFile = new File(workdir, "truststore.p12");
//        try (
//    FileOutputStream fos = new FileOutputStream(fTruststoreFile)) {
//        fos.write(certificateService.getTrustStorePkcs12());
//    }
//

}
