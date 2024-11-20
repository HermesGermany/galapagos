package com.hermesworld.ais.galapagos.security.roles.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.security.roles.ApplicationGrantedAuthority;
import com.hermesworld.ais.galapagos.security.roles.AuthorizationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationServiceImpl implements AuthorizationService {

    private final ApplicationsService applicationsService;

    public AuthorizationServiceImpl(ApplicationsService applicationsService) {
        this.applicationsService = applicationsService;
    }

    @Override
    public boolean canView(String environmentId) {
        return true;
    }

    @Override
    public boolean canEdit(String environmentId) {
        return true;
    }

    @Override
    public boolean canEditApplication(String applicationId) {
        return applicationsService.isUserAuthorizedFor(applicationId);
    }

    @Override
    public boolean canGenerateNewApiKey(String applicationId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .filter(authority -> authority instanceof ApplicationGrantedAuthority)
                .map(authority -> (ApplicationGrantedAuthority) authority)
                .anyMatch(appAuthority -> "ROLE_GENERATE_API_KEY".equals(appAuthority.getAuthority())
                        && applicationId.equals(appAuthority.getApplicationId()));
    }
}
