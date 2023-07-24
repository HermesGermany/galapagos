package com.hermesworld.ais.galapagos.security;

import java.util.Optional;

public interface CurrentUserService {

    Optional<String> getCurrentUserName();

    Optional<AuditPrincipal> getCurrentPrincipal();

    Optional<String> getCurrentUserEmailAddress();

    /**
     * Checks from the Security Context, if the current user has the role of an Administrator
     *
     * @return <code>true</code> if the current user has the role of an Administrator, <code>false</code> otherwise
     */
    boolean isAdmin();

}
