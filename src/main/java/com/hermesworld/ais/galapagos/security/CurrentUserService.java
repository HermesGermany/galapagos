package com.hermesworld.ais.galapagos.security;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Optional;

public interface CurrentUserService {

    Optional<String> getCurrentUserName();

     Optional<AuditPrincipal> getCurrentPrincipal();

     Optional<String> getCurrentUserEmailAddress();

     Optional<OidcUser> getOIDCUser();

     boolean isAdmin();

}
