package com.hermesworld.ais.galapagos.security.impl;

import com.hermesworld.ais.galapagos.events.EventContextSource;
import com.hermesworld.ais.galapagos.security.AuditPrincipal;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.security.config.GalapagosSecurityProperties;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class DefaultCurrentUserService implements CurrentUserService, EventContextSource {

    private final GalapagosSecurityProperties securityConfig;

    public DefaultCurrentUserService(GalapagosSecurityProperties securityConfig) {
        this.securityConfig = securityConfig;
    }

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
        return getAuthenticationToken()
                .map(t -> new AuditPrincipal(t.getToken().getClaimAsString(securityConfig.getJwtUserNameClaim()),
                        t.getToken().getClaimAsString(securityConfig.getJwtDisplayNameClaim())));
    }

    @Override
    public Optional<String> getCurrentUserEmailAddress() {
        return getAuthenticationToken()
                .map(token -> token.getToken().getClaimAsString(securityConfig.getJwtEmailClaim()));
    }

    private Optional<JwtAuthenticationToken> getAuthenticationToken() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context.getAuthentication() instanceof JwtAuthenticationToken token) {
            return Optional.of(token);
        }

        return Optional.empty();
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
    public boolean isAdmin() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context.getAuthentication() == null || context.getAuthentication().getAuthorities() == null) {
            return false;
        }
        return context.getAuthentication().getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
    }

}
