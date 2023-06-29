package com.hermesworld.ais.galapagos.security.impl;

import com.hermesworld.ais.galapagos.events.EventContextSource;
import com.hermesworld.ais.galapagos.security.AuditPrincipal;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.keycloak.representations.IDToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class DefaultCurrentUserService implements CurrentUserService, EventContextSource {

    @Override
    public Optional<String> getCurrentUserName() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context.getAuthentication() == null || context.getAuthentication().getPrincipal() == null) {
            return Optional.empty();
        }

        return Optional.of(context.getAuthentication().getName());
    }

    @Override
    public Optional<AuditPrincipal> getCurrentPrincipal() {
        return getKeycloakIDToken().map(token -> new AuditPrincipal(token.getPreferredUsername(), token.getName()));
    }

    @Override
    public Optional<String> getCurrentUserEmailAddress() {
        return getKeycloakIDToken().map(token -> token.getEmail())
                .flatMap(s -> ObjectUtils.isEmpty(s) ? Optional.empty() : Optional.of(s));
    }

    private Optional<IDToken> getKeycloakIDToken() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context.getAuthentication() == null || context.getAuthentication().getPrincipal() == null
                || !(context.getAuthentication() instanceof KeycloakAuthenticationToken)) {
            return Optional.empty();
        }

        KeycloakAuthenticationToken principal = (KeycloakAuthenticationToken) context.getAuthentication();
        KeycloakSecurityContext keycloakContext = principal.getAccount().getKeycloakSecurityContext();
        IDToken token = keycloakContext.getIdToken();
        if (token == null) {
            token = keycloakContext.getToken();
        }
        return Optional.ofNullable(token);
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
        return context.getAuthentication().getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
    }

}
