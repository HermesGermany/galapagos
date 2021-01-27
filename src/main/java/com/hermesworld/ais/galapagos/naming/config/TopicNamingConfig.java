package com.hermesworld.ais.galapagos.naming.config;

import com.hermesworld.ais.galapagos.naming.config.AdditionNamingRules;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents the naming schema for one type of API Topics (Events, Commands, or Data).
 */
@Getter
@Setter
public class TopicNamingConfig {

    private String nameFormat;

    private AdditionNamingRules additionRules;

}
