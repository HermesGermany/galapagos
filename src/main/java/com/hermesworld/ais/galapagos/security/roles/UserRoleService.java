package com.hermesworld.ais.galapagos.security.roles;

import com.hermesworld.ais.galapagos.applications.RequestState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for managing user roles in the system. Provides methods for adding, retrieving, and removing user
 * roles
 */
public interface UserRoleService {

    List<UserRoleData> getAllRoles(String environmentId);

    List<UserRoleData> getRolesForUser(String environmentId, String userName);

    CompletableFuture<Void> deleteUserRoles(String environmentId, String userName);

    Map<String, List<UserRoleData>> getAllRolesForCurrentUser();

    CompletableFuture<Void> deleteUserRoleById(String environmentId, String id);

    CompletableFuture<UserRoleData> updateRole(String requestId, String environmentId, RequestState newState);

    List<UserRoleData> listAllRoles();

    CompletableFuture<UserRoleData> submitRoleRequest(String applicationId, Role role, String environmentId,
            String comments);

    CompletableFuture<Boolean> cancelUserRoleRequest(String requestId, String environmentId);
}
