package com.hermesworld.ais.galapagos.security.config;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties to fine-control how verified JWT tokens are mapped to username and roles. See
 * application.properties for details.
 */
@Configuration
@ConfigurationProperties("galapagos.security")
@Validated
@Getter
@Setter
public class GalapagosSecurityProperties {

    @NotEmpty
    private String jwtRoleClaim;

    @NotEmpty
    private String jwtUserNameClaim;

    @NotEmpty
    private String jwtDisplayNameClaim;

    @NotEmpty
    private String jwtEmailClaim;

}
