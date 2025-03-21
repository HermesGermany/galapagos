package com.hermesworld.ais.galapagos.security.controller;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ConfluentApiException;
import com.hermesworld.ais.galapagos.security.roles.UserRoleData;
import com.hermesworld.ais.galapagos.security.roles.UserRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.cert.CertificateException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@Slf4j
public class RoleController {

    private final UserRoleService userRoleService;

    private final ApplicationsService applicationsService;

    public RoleController(UserRoleService userRoleService, ApplicationsService applicationsService) {
        this.userRoleService = userRoleService;
        this.applicationsService = applicationsService;
    }

    @GetMapping(value = "/api/me/roles", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RoleDto> listUserRoles() {
        return userRoleService.getAllRolesForCurrentUser().entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(this::toRoleDto)).collect(Collectors.toList());
    }

    @GetMapping(value = "/api/me/roles/{environmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RoleDto> getAllRoles(@PathVariable String environmentId) {
        return userRoleService.getAllRoles(environmentId).stream().map(this::toRoleDto).collect(Collectors.toList());
    }

    @GetMapping(value = "/api/roles/{environmentId}/{userName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RoleDto> getRolesForUser(@PathVariable String environmentId, @PathVariable String userName) {
        return userRoleService.getRolesForUser(environmentId, userName).stream().map(this::toRoleDto)
                .collect(Collectors.toList());
    }

    @DeleteMapping(value = { "/api/roles/{environmentId}/{userName}" }, produces = MediaType.APPLICATION_JSON_VALUE)
    public void deleteUserRoles(

            @PathVariable String environmentId,

            @PathVariable String userName) {
        try {
            userRoleService.deleteUserRoles(environmentId, userName).get();
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e, "Failed to delete the user role: ");
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @DeleteMapping(value = { "/api/roles/{environmentId}/prefixes/{id}" }, produces = MediaType.APPLICATION_JSON_VALUE)
    public void deleteUserRoleById(

            @PathVariable String environmentId, @PathVariable String id) {
        try {
            userRoleService.deleteUserRoleById(environmentId, id).get();
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e, "Failed to delete the user role: ");
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PostMapping(value = "/api/admin/roles/requests/{id}/{environmentId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    // @Secured("ROLE_ADMIN")
    public UserRoleData updateRole(@PathVariable String id, @PathVariable String environmentId,
            @RequestBody UpdateRoleRequestDto updateData) {
        try {
            return userRoleService.updateRole(id, environmentId, updateData.getNewState()).get();
        }
        catch (InterruptedException e) {
            return null;
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e, "Could not update role request: ");
        }
    }

    @DeleteMapping(value = "/api/me/roles/requests/{id}/{environmentId}")
    public void cancelUserRoleRequest(@PathVariable String id, @PathVariable String environmentId)
            throws ExecutionException {
        try {
            Boolean b = userRoleService.cancelUserRoleRequest(id, environmentId).get();
            if (!b) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
        catch (ExecutionException e) {
            throw new ExecutionException("Could not retrieve user's requests: ", e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @GetMapping(value = "/api/admin/roles", produces = MediaType.APPLICATION_JSON_VALUE)
    // @Secured("ROLE_ADMIN")
    public List<UserRoleData> listAllRoles() {
        return userRoleService.listAllRoles();
    }

    @PutMapping(value = "/api/me/roles/requests", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public UserRoleData submitRoleRequest(@RequestBody RoleRequestSubmissionDto request) {
        if (!StringUtils.hasLength(request.getApplicationId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Required parameter applicationId is missing or empty");
        }

        if (request.getEnvironmentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Required parameter environmentId is missing or empty");
        }

        if (request.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required parameter role is missing or empty");
        }

        applicationsService.getKnownApplication(request.getApplicationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        try {
            return userRoleService.submitRoleRequest(request.getApplicationId(), request.getRole(),
                    request.getEnvironmentId(), request.getComments()).get();
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e, "Could not submit role request: ");
        }
        catch (InterruptedException e) {
            return null;
        }
    }

    private RoleDto toRoleDto(UserRoleData role) {
        if (role == null) {
            return null;
        }
        return new RoleDto(role.getId(), role.getUserName(), role.getRole(), role.getEnvironmentId(),
                role.getApplicationId(), role.getComments(), role.getCreatedAt(), role.getNotificationEmailAddress(),
                role.getLastStatusChangeAt(), role.getLastStatusChangeBy(), role.getState());
    }

    private ResponseStatusException handleExecutionException(ExecutionException e, String msgPrefix) {
        Throwable t = e.getCause();
        if (t instanceof CertificateException) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, msgPrefix + t.getMessage());
        }
        if (t instanceof ConfluentApiException) {
            log.error("Encountered Confluent API Exception", t);
            return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (t instanceof NoSuchElementException) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if ((t instanceof IllegalStateException) || (t instanceof IllegalArgumentException)) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, t.getMessage());
        }

        log.error("Unhandled exception in RoleController", t);
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
