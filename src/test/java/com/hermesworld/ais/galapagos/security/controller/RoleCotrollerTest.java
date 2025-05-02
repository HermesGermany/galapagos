package com.hermesworld.ais.galapagos.security.controller;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.security.roles.Role;
import com.hermesworld.ais.galapagos.security.roles.UserRoleData;
import com.hermesworld.ais.galapagos.security.roles.UserRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

class RoleControllerTest {

    private UserRoleService userRoleService;

    private ApplicationsService applicationsService;

    private RoleController roleController;

    @BeforeEach
    void setup() {
        userRoleService = mock(UserRoleService.class);
        applicationsService = mock(ApplicationsService.class);
        roleController = new RoleController(userRoleService, applicationsService);
    }

    @Test
    @DisplayName("it should list all roles for the current user")
    void testListUserRoles() {
        UserRoleData role1 = new UserRoleData();
        role1.setUserName("user1");
        role1.setRole(Role.ADMIN);

        UserRoleData role2 = new UserRoleData();
        role2.setUserName("user1");
        role2.setRole(Role.TESTER);

        when(userRoleService.getAllRolesForCurrentUser()).thenReturn(Map.of("env1", List.of(role1, role2)));

        List<RoleDto> result = roleController.listUserRoles();
        assertEquals(2, result.size());
        assertEquals(Role.ADMIN, result.get(0).getRole());
        assertEquals(Role.TESTER, result.get(1).getRole());
    }

    @Test
    @DisplayName("it should list all roles for a specific environment")
    void testGetAllRolesForEnvironment() {
        UserRoleData role = new UserRoleData();
        role.setUserName("user1");
        role.setRole(Role.ADMIN);
        role.setEnvironmentId("env1");

        when(userRoleService.getAllRoles("env1")).thenReturn(List.of(role));

        List<RoleDto> result = roleController.getAllRoles("env1");
        assertEquals(1, result.size());
        assertEquals(Role.ADMIN, result.get(0).getRole());
        assertEquals("env1", result.get(0).getEnvironmentId());
    }

    @Test
    @DisplayName("it should delete user roles successfully")
    void testDeleteUserRoles() throws Exception {
        when(userRoleService.deleteUserRoles("env1", "user1")).thenReturn(CompletableFuture.completedFuture(null));

        roleController.deleteUserRoles("env1", "user1");
        verify(userRoleService, times(1)).deleteUserRoles("env1", "user1");
    }

    @Test
    @DisplayName("it should handle errors when deleting user roles")
    void testDeleteUserRoles_error() {
        when(userRoleService.deleteUserRoles("env1", "user1"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalArgumentException("Invalid input")));

        try {
            roleController.deleteUserRoles("env1", "user1");
            fail("Expected ResponseStatusException");
        }
        catch (ResponseStatusException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }
    }

    @Test
    @DisplayName("it should submit a role request successfully")
    void testSubmitRoleRequest() {
        RoleRequestSubmissionDto request = new RoleRequestSubmissionDto();
        request.setApplicationId("app1");
        request.setEnvironmentId("env1");
        request.setRole(Role.TESTER);
        request.setComments("Test request");

        UserRoleData roleData = new UserRoleData();
        roleData.setApplicationId("app1");
        roleData.setRole(Role.TESTER);

        when(applicationsService.getKnownApplication("app1")).thenReturn(Optional.of(mock(KnownApplication.class)));
        when(userRoleService.submitRoleRequest("app1", Role.TESTER, "env1", "Test request"))
                .thenReturn(CompletableFuture.completedFuture(roleData));

        UserRoleData result = roleController.submitRoleRequest(request);
        assertEquals("app1", result.getApplicationId());
        assertEquals(Role.TESTER, result.getRole());
    }

    @Test
    @DisplayName("it should validate required fields when submitting a role request")
    void testSubmitRoleRequest_validation() {
        RoleRequestSubmissionDto request = new RoleRequestSubmissionDto();
        request.setApplicationId("");
        request.setEnvironmentId(null);
        request.setRole(null);

        try {
            roleController.submitRoleRequest(request);
            fail("Expected ResponseStatusException");
        }
        catch (ResponseStatusException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }
    }

    @Test
    @DisplayName("it should update a role successfully")
    void testUpdateRole() {
        UpdateRoleRequestDto updateData = new UpdateRoleRequestDto();
        updateData.setNewState(RequestState.APPROVED);

        UserRoleData roleData = new UserRoleData();
        roleData.setId("req1");
        roleData.setState(RequestState.APPROVED);

        when(userRoleService.updateRole("req1", "env1", RequestState.APPROVED))
                .thenReturn(CompletableFuture.completedFuture(roleData));

        UserRoleData result = roleController.updateRole("req1", "env1", updateData);
        assertEquals(RequestState.APPROVED, result.getState());
    }

    @Test
    @DisplayName("it should handle errors when updating a role")
    void testUpdateRole_error() {
        UpdateRoleRequestDto updateData = new UpdateRoleRequestDto();
        updateData.setNewState(RequestState.APPROVED);

        when(userRoleService.updateRole("req1", "env1", RequestState.APPROVED))
                .thenReturn(CompletableFuture.failedFuture(new IllegalArgumentException("Invalid state")));

        try {
            roleController.updateRole("req1", "env1", updateData);
            fail("Expected ResponseStatusException");
        }
        catch (ResponseStatusException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }
    }
}