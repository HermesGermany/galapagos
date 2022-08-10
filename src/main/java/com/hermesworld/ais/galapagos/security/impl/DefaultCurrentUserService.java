package com.hermesworld.ais.galapagos.security.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.hermesworld.ais.galapagos.events.EventContextSource;
import com.hermesworld.ais.galapagos.security.AuditPrincipal;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class DefaultCurrentUserService implements CurrentUserService, EventContextSource {
    @Autowired
    private OAuth2AuthorizedClientService clientService;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;

    private static final String ROLES_KEY = "roles";

    @Override
    public Optional<String> getCurrentUserName() {
        return getOIDCUser().map(oidcUser -> oidcUser.getPreferredUsername())
                .flatMap(s -> StringUtils.isEmpty(s) ? Optional.empty() : Optional.of(s));
    }

    @Override
    public Optional<AuditPrincipal> getCurrentPrincipal() {
        return getOIDCUser()
                .map(user -> new AuditPrincipal(user.getIdToken().getGivenName(), user.getIdToken().getFullName()));
    }

    @Override
    public Optional<String> getCurrentUserEmailAddress() {
        return getOIDCUser().map(user -> user.getEmail())
                .flatMap(s -> StringUtils.isEmpty(s) ? Optional.empty() : Optional.of(s));
    }

    @Override
    public Map<String, Object> getContextValues() {
        Map<String, Object> result = new HashMap<>();
        result.put("username", getCurrentUserName().orElse(null));
        result.put("email", getCurrentUserEmailAddress().orElse(null));
        result.put("principal", getCurrentPrincipal().orElse(null));
        return result;
    }

    @Override
    /**
     * Checks from the Security Context, if the current user has the role of an Administrator
     *
     * @return true if the current user has the role of an Administrator, else false
     */

    public boolean isAdmin() {
        SecurityContext context = SecurityContextHolder.getContext();

        if (context.getAuthentication() == null || context.getAuthentication().getAuthorities() == null) {
            return false;
        }
        SecurityContext securityContext = SecurityContextHolder.getContext();
        OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) securityContext.getAuthentication();
        OAuth2AuthorizedClient client = clientService
                .loadAuthorizedClient(oauth2Token.getAuthorizedClientRegistrationId(), oauth2Token.getName());

        String token = client.getAccessToken().getTokenValue();
        DecodedJWT jwt = JWT.decode(token);

        return new JSONObject(jwt.getClaim("resource_access").toString()).getJSONObject(clientId)
                .getJSONArray(ROLES_KEY).toList().contains("admin");
    }

    private Optional<OidcUser> getOIDCUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null
                && authentication.getPrincipal() instanceof OidcUser) {
            OidcUser oidcUser = (OidcUser) authentication.getPrincipal();

            return Optional.of(oidcUser);
        }
        return Optional.empty();
    }

}
