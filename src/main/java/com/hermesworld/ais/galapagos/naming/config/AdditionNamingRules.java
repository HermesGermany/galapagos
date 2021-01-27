package com.hermesworld.ais.galapagos.naming.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents the naming rules applying to the "addition" in the name of the API topic, so the "free" part after the
 * prefix given by naming configuration.
 */
@Getter
@Setter
public class AdditionNamingRules {

    private boolean allowPascalCase;

    private boolean allowCamelCase;

    private boolean allowKebabCase;

    private boolean allowSnakeCase;

    private String allowedSeparators;

}
