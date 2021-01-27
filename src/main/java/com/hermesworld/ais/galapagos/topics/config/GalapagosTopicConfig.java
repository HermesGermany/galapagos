package com.hermesworld.ais.galapagos.topics.config;

import java.time.Period;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Represents all (technical) configuration elements related to Topics. <br>
 * Default values can be found in resource file <code>application.properties</code>. <br>
 * For Naming Rules, see {@link com.hermesworld.ais.galapagos.naming.config.NamingConfig}.
 */
@Configuration
@ConfigurationProperties("galapagos.topics")
@Getter
@Setter
public class GalapagosTopicConfig {

    private int maxPartitionCount;

    private int defaultPartitionCount;

    private Period minDeprecationTime;

}
