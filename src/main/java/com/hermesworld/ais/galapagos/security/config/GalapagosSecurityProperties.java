package com.hermesworld.ais.galapagos.security.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
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
@Slf4j
public class GalapagosSecurityProperties implements Validator {

    // @NotEmpty
    private String jwtRoleClaim;

    // @NotEmpty
    private String jwtUserNameClaim;

    // @NotEmpty
    private String jwtDisplayNameClaim;

    // @NotEmpty
    private String jwtEmailClaim;

    @Override
    public boolean supports(@NonNull Class<?> clazz) {
        return GalapagosSecurityProperties.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(@NonNull Object target, @NonNull Errors errors) {
        // TODO remove, and replace by the NotEmpty annotations, once we do no longer want to provide this message.
        if (target instanceof GalapagosSecurityProperties properties) {
            if (ObjectUtils.isEmpty(properties.getJwtRoleClaim())
                    || ObjectUtils.isEmpty(properties.getJwtUserNameClaim())
                    || ObjectUtils.isEmpty(properties.getJwtDisplayNameClaim())
                    || ObjectUtils.isEmpty(properties.getJwtEmailClaim())) {
                errors.reject("MISSING_OAUTH2_PROPERTIES",
                        "Missing Galapagos OAuth2 properties. Maybe you did not perform required migration steps for Galapagos 2.8.0?\nPlease refer to https://github.com/HermesGermany/galapagos/blob/main/docs/Migration%20Guide%202.8.md");
            }
        }
    }
}
