package com.hermesworld.ais.galapagos.security.roles;

import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class ApplicationAccessDecisionManager implements AccessDecisionManager {

    @Override
    public void decide(Authentication authentication, Object object, Collection<ConfigAttribute> configAttributes)
            throws AccessDeniedException {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }

        for (ConfigAttribute attribute : configAttributes) {
            String requiredAuthority = attribute.getAttribute();
            if (requiredAuthority == null)
                continue;

            for (GrantedAuthority authority : authentication.getAuthorities()) {
                if (authority instanceof ApplicationGrantedAuthority appAuthority) {
                    if (requiredAuthority.equals(appAuthority.getAuthority()) && object instanceof String applicationId
                            && applicationId.equals(appAuthority.getApplicationId())) {
                        return;
                    }
                }
            }
        }

        throw new AccessDeniedException("Access denied: insufficient permissions.");
    }

    @Override
    public boolean supports(ConfigAttribute attribute) {
        return true;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return true;
    }
}
