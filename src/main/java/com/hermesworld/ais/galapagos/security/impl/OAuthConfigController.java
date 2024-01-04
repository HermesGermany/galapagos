package com.hermesworld.ais.galapagos.security.impl;

import com.hermesworld.ais.galapagos.security.config.GalapagosSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Slf4j
public class OAuthConfigController {

    private final OAuth2ClientProperties oAuth2ClientProperties;

    private final GalapagosSecurityProperties galapagosSecurityProperties;

    public OAuthConfigController(OAuth2ClientProperties oAuth2ClientProperties,
            GalapagosSecurityProperties galapagosSecurityProperties) {
        this.oAuth2ClientProperties = oAuth2ClientProperties;
        this.galapagosSecurityProperties = galapagosSecurityProperties;
    }

    @GetMapping(value = "/oauth2/config.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getOauthConfig() {
        Map<String, OAuth2ClientProperties.Registration> registrations = oAuth2ClientProperties.getRegistration();
        if (registrations.isEmpty()) {
            log.error("No Spring Security OAuth2 client registrations found. Cannot provide OAuth2 config to UI.");
            return Map.of();
        }
        if (registrations.size() > 1) {
            log.error(
                    "More than one Spring Security OAuth2 client registration found. Cannot provide OAuth2 config to UI.");
            return Map.of();
        }
        var regKey = registrations.entrySet().iterator().next().getKey();
        var registration = registrations.get(regKey);
        var provider = oAuth2ClientProperties.getProvider().get(regKey);

        if (provider == null) {
            log.error("No Spring Security OAuth2 provider found with id \"{}\". Cannot provide OAuth2 config to UI.",
                    regKey);
            return Map.of();
        }

        // Use .well-known endpoint to retrieve correct token endpoint
        var fullReg = ClientRegistrations.fromOidcIssuerLocation(provider.getIssuerUri())
                .clientId(registration.getClientId()).build();

        return Map.of("clientId", registration.getClientId(), "scope", registration.getScope(), "issuerUri",
                provider.getIssuerUri(), "tokenEndpoint", fullReg.getProviderDetails().getTokenUri(), "userNameClaim",
                galapagosSecurityProperties.getJwtUserNameClaim(), "displayNameClaim",
                galapagosSecurityProperties.getJwtDisplayNameClaim(), "rolesClaim",
                galapagosSecurityProperties.getJwtRoleClaim());
    }

}
