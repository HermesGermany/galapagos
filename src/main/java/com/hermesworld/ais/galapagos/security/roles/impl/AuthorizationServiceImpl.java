package com.hermesworld.ais.galapagos.security.roles.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.security.roles.ApplicationGrantedAuthority;
import com.hermesworld.ais.galapagos.security.roles.AuthorizationService;
import com.hermesworld.ais.galapagos.security.roles.Role;
import com.hermesworld.ais.galapagos.security.roles.UserRoleService;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service("authorizationService")
public class AuthorizationServiceImpl implements AuthorizationService {

    private final ApplicationsService applicationsService;

    private final TopicService topicService;

    private final UserRoleService userRoleService;

    public AuthorizationServiceImpl(ApplicationsService applicationsService, TopicService topicService,
                                    UserRoleService userRoleService) {
        this.applicationsService = applicationsService;
        this.topicService = topicService;
        this.userRoleService = userRoleService;
    }

    @Override
    public boolean canView(String environmentId, String topicName, String applicationId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String userName = authentication.getName();
        if (applicationId == null) {
            TopicMetadata metadata = topicService.getTopic(environmentId, topicName).orElse(null);
            if (metadata == null) {
                return false;
            }
            applicationId = metadata.getOwnerApplicationId();
        }
        String finalApplicationId = applicationId;
        return userRoleService.getRolesForUser(environmentId, userName).stream().anyMatch(
                role -> (role.getRole() == Role.VIEWER || role.getRole() == Role.TESTER || role.getRole() == Role.ADMIN)
                        && Objects.equals(role.getApplicationId(), finalApplicationId));
    }

    @Override
    public boolean canEdit(String environmentId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String userName = authentication.getName();
        return userRoleService.getRolesForUser(environmentId, userName).stream()
                .anyMatch(role -> (role.getRole() == Role.ADMIN)
                        || (role.getRole() == Role.TESTER && !"prod".equals(environmentId)));
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
