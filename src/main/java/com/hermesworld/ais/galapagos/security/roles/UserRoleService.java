package com.hermesworld.ais.galapagos.security.roles;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for managing user roles in the system. Provides methods for adding, retrieving, and removing user
 * roles,
 */
public interface UserRoleService {

    CompletableFuture<Void> addUserRole(String environmentId, String userName, Role role, String applicationId);

    List<UserRoleData> getAllRoles(String environmentId);

    List<UserRoleData> getRolesForUser(String environmentId, String userName);

    CompletableFuture<Void> deleteUserRole(String environmentId, UserRoleData value);
}
