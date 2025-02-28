package com.hermesworld.ais.galapagos.security.controller;

import com.hermesworld.ais.galapagos.security.roles.UserRoleData;
import com.hermesworld.ais.galapagos.security.roles.UserRoleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
public class RoleController {

    private final UserRoleService userRoleService;

    public RoleController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @GetMapping(value = "/api/me/roles", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RoleDto> listUserRoles() {
        return userRoleService.getAllRolesForCurrentUser().entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(this::toRoleDto)).collect(Collectors.toList());
    }

    @GetMapping(value = "/api/me/roles/{environmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RoleDto> getAllRoles(@PathVariable String environmentId) {
        return userRoleService.getAllRoles(environmentId).stream().map(this::toRoleDto)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/api/roles/{environmentId}/{userName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RoleDto> getRolesForUser(@PathVariable String environmentId, @PathVariable String userName) {
        return userRoleService.getRolesForUser(environmentId, userName).stream().map(this::toRoleDto)
                .collect(Collectors.toList());
    }

    @PutMapping(value = "/api/roles/{environmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Void> addUserRole(@PathVariable String environmentId,
                                               @RequestBody CreateUserRoleDto data) {
        if (data.getUserName() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing username");
        }

        if (data.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing role");
        }
        return userRoleService.addUserRole(environmentId, toUserRoleData(data));
    }

    @DeleteMapping(value = {"/api/roles/{environmentId}/{userName}"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Void> deleteUserRoles(

            @PathVariable String environmentId,

            @PathVariable String userName) {
        return userRoleService.deleteUserRoles(environmentId, userName);
    }

    @DeleteMapping(value = {"/api/roles/{environmentId}/prefixes/{id}"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Void> deleteUserRoleById(

            @PathVariable String environmentId, @PathVariable String id) {
        return userRoleService.deleteUserRoleById(environmentId, id);
    }

    @PostMapping(value = "/api/admin/role/requests/{id}/{environmentId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    //@Secured("ROLE_ADMIN")
    public CompletableFuture<Void> updateRole(@PathVariable String id,
                                              @PathVariable String environmentId,
                                              @RequestBody UserRoleData updateData) {
        return userRoleService.updateRole(id, environmentId, updateData.getState());
    }

    @GetMapping(value = "/api/admin/roles", produces = MediaType.APPLICATION_JSON_VALUE)
    //@Secured("ROLE_ADMIN")
    public List<UserRoleData> listAllRoles() {
        return userRoleService.listAllRoles();
    }

    private UserRoleData toUserRoleData(CreateUserRoleDto data) {
        UserRoleData userRoleData = new UserRoleData();
        userRoleData.setUserName(data.getUserName());
        userRoleData.setRole(data.getRole());
        userRoleData.setApplicationId(data.getApplicationId());
        userRoleData.setComments(data.getComments());
        return userRoleData;
    }

    private RoleDto toRoleDto(UserRoleData role) {
        if (role == null) {
            return null;
        }
        return new RoleDto(role.getId(), role.getUserName(), role.getRole(), role.getEnvironment(), role.getApplicationId(), role.getComments(), role.getCreatedAt(), role.getNotificationEmailAddress(), role.getLastStatusChangeAt(), role.getLastStatusChangeBy(), role.getState());
    }

}
