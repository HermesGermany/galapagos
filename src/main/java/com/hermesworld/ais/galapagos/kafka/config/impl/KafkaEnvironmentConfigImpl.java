package com.hermesworld.ais.galapagos.kafka.config.impl;

import com.hermesworld.ais.galapagos.ccloud.auth.ConfluentCloudAuthConfig;
import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationConfig;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import lombok.Getter;
import lombok.Setter;

@Setter
public class KafkaEnvironmentConfigImpl implements KafkaEnvironmentConfig {

    @Getter(onMethod = @__({ @Override }))
    private String id;

    @Getter(onMethod = @__({ @Override }))
    private String name;

    @Getter(onMethod = @__({ @Override }))
    private String bootstrapServers;

    @Getter(onMethod = @__({ @Override }))
    private boolean stagingOnly;

    @Getter(onMethod = @__({ @Override }))
    private String authenticationMode;

    @Getter
    private ConfluentCloudAuthConfig ccloud;

    @Getter
    private CertificatesAuthenticationConfig certificates;

}
