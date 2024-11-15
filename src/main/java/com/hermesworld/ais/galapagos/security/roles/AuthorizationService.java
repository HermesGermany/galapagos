package com.hermesworld.ais.galapagos.security.roles;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {
    private final ApplicationsService applicationsService;

    public AuthorizationService(ApplicationsService applicationsService) {
        this.applicationsService = applicationsService;
    }

    public boolean canView(String envId) {
        System.out.println(envId);
        return true;
    }

    public boolean canEdit(String envId) {
        System.out.println(envId);
        return true;
    }

    public boolean canEditApplication(String appId) {
        return applicationsService.isUserAuthorizedFor(appId);
    }

    public boolean canGenerateNewApiKey(String applicationId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .filter(authority -> authority instanceof ApplicationGrantedAuthority)
                .map(authority -> (ApplicationGrantedAuthority) authority)
                .anyMatch(appAuthority ->
                        "ROLE_GENERATE_API_KEY".equals(appAuthority.getAuthority()) &&
                                applicationId.equals(appAuthority.getApplicationId()));
    }

}
