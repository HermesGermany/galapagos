package com.hermesworld.ais.galapagos.security;

import java.util.Optional;

public interface CurrentUserService {

    Optional<String> getCurrentUserName();

     Optional<AuditPrincipal> getCurrentPrincipal();

     Optional<String> getCurrentUserEmailAddress();

     boolean isAdmin();

}
