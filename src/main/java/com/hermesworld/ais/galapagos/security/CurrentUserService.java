package com.hermesworld.ais.galapagos.security;

import java.util.Optional;

public interface CurrentUserService {

	public Optional<String> getCurrentUserName();

	public Optional<AuditPrincipal> getCurrentPrincipal();

	public Optional<String> getCurrentUserEmailAddress();

	public boolean isAdmin();

}
