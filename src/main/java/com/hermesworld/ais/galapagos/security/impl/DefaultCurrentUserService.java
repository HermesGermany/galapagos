package com.hermesworld.ais.galapagos.security.impl;

import com.hermesworld.ais.galapagos.events.EventContextSource;
import com.hermesworld.ais.galapagos.security.AuditPrincipal;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class DefaultCurrentUserService implements CurrentUserService, EventContextSource {

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
        return context.getAuthentication().getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
    }

    @Override
    public Optional<OidcUser> getOIDCUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        System.out.println(authentication);
        if (authentication != null && authentication.getPrincipal() != null
                && authentication.getPrincipal() instanceof OidcUser) {
            OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
            System.out.println(oidcUser);
            return Optional.of(oidcUser);
        }
        return Optional.empty();
    }

}
