package com.hermesworld.ais.galapagos.security.roles.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.events.GalapagosEventManagerMock;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.security.roles.Role;
import com.hermesworld.ais.galapagos.security.roles.UserRoleData;
import com.hermesworld.ais.galapagos.util.TimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserRoleServiceImplTest {

    private KafkaClusters kafkaClusters;

    private TopicBasedRepository<UserRoleData> roleRepository;

    private TopicBasedRepository<UserRoleData> roleRepository2;

    private CurrentUserService currentUserService;

    private ApplicationsService applicationsService;

    private TimeService timeService;

    @BeforeEach
    void setupMocks() {
        kafkaClusters = mock(KafkaClusters.class);
        roleRepository = new TopicBasedRepositoryMock<>();
        roleRepository2 = new TopicBasedRepositoryMock<>();
        currentUserService = mock(CurrentUserService.class);
        applicationsService = mock(ApplicationsService.class);
        timeService = mock(TimeService.class);

        KafkaCluster testCluster = mock(KafkaCluster.class);
        KafkaCluster testCluster2 = mock(KafkaCluster.class);
        when(testCluster.getRepository("user-roles", UserRoleData.class)).thenReturn(roleRepository);
        when(testCluster2.getRepository("user-roles", UserRoleData.class)).thenReturn(roleRepository2);
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
        when(kafkaClusters.getEnvironment("test2")).thenReturn(Optional.of(testCluster2));
        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("test", "test2"));
        when(timeService.getTimestamp()).thenReturn(ZonedDateTime.now());
    }

    @Test
    void testListAllRoles() {
        UserRoleData role1 = createRequestDao("req-1", "test", "app-1", Role.TESTER, ZonedDateTime.now(), "user1");
        UserRoleData role2 = createRequestDao("req-2", "test", "app-1", Role.ADMIN, ZonedDateTime.now(), "user2");
        UserRoleData role3 = createRequestDao("req-3", "test2", "app-3", Role.VIEWER, ZonedDateTime.now(), "user3");
        roleRepository.save(role1);
        roleRepository.save(role2);
        roleRepository2.save(role3);
        UserRoleServiceImpl service = new UserRoleServiceImpl(kafkaClusters, currentUserService, applicationsService,
                new GalapagosEventManagerMock(), timeService);

        List<UserRoleData> roles = service.listAllRoles();
        assertNotNull(roles);
        assertEquals(3, roles.size());
        assertTrue(roleRepository.getObject("req-1").isPresent());
        assertTrue(roleRepository.getObject("req-2").isPresent());
        assertTrue(roleRepository2.getObject("req-3").isPresent());
        assertTrue(roles.stream().anyMatch(r -> r.getId().equals("req-1")));
        assertTrue(roles.stream().anyMatch(r -> r.getId().equals("req-2")));
        assertTrue(roles.stream().anyMatch(r -> r.getId().equals("req-3")));
    }

    @Test
    void testSubmitRoleRequest() throws Exception {
        when(currentUserService.getCurrentUserName()).thenReturn(Optional.of("tester"));
        when(applicationsService.getKnownApplication("app-1")).thenReturn(Optional.of(mock(KnownApplication.class)));

        UserRoleServiceImpl service = new UserRoleServiceImpl(kafkaClusters, currentUserService, applicationsService,
                new GalapagosEventManagerMock(), timeService);

        UserRoleData result = service.submitRoleRequest("app-1", Role.TESTER, "test", "Please approve").get();

        assertNotNull(result);
        assertEquals("app-1", result.getApplicationId());
        assertEquals(Role.TESTER, result.getRole());
        assertEquals("test", result.getEnvironmentId());
        assertEquals(RequestState.SUBMITTED, result.getState());
        assertEquals("tester", result.getUserName());

        Optional<UserRoleData> saved = roleRepository.getObject(result.getId());
        assertTrue(saved.isPresent());
        assertEquals(result.getId(), saved.get().getId());
    }

    @Test
    void testSubmitRoleRequest_roleAlreadyExist() throws ExecutionException, InterruptedException {
        when(currentUserService.getCurrentUserName()).thenReturn(Optional.of("tester"));
        when(applicationsService.getKnownApplication("app-1")).thenReturn(Optional.of(mock(KnownApplication.class)));

        UserRoleServiceImpl service = new UserRoleServiceImpl(kafkaClusters, currentUserService, applicationsService,
                new GalapagosEventManagerMock(), timeService);

        service.submitRoleRequest("app-1", Role.TESTER, "test", "Please approve").get();
        CompletableFuture<UserRoleData> future = service.submitRoleRequest("app-1", Role.ADMIN, "test",
                "Please approve");
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, executionException.getCause());
        assertEquals("A role for this application has been already submitted.",
                executionException.getCause().getMessage());
    }

    @Test
    void testUpdateRole() throws Exception {
        when(currentUserService.getCurrentUserName()).thenReturn(Optional.of("tester"));

        UserRoleData request = new UserRoleData();
        request.setId("req-1");
        request.setApplicationId("app-1");
        request.setUserName("user1");
        request.setRole(Role.ADMIN);
        request.setEnvironmentId("test");
        request.setState(RequestState.SUBMITTED);
        request.setCreatedAt(ZonedDateTime.now().minusDays(1));
        roleRepository.save(request);

        UserRoleServiceImpl service = new UserRoleServiceImpl(kafkaClusters, currentUserService, applicationsService,
                new GalapagosEventManagerMock(), timeService);

        UserRoleData updated = service.updateRole("req-1", "test", RequestState.APPROVED).get();

        assertEquals(RequestState.APPROVED, updated.getState());
        assertEquals("tester", updated.getLastStatusChangeBy());
        assertNotNull(updated.getLastStatusChangeAt());

        Optional<UserRoleData> saved = roleRepository.getObject("req-1");
        assertTrue(saved.isPresent());
        assertEquals(RequestState.APPROVED, saved.get().getState());
    }

    @Test
    void testCancelRoleRequest() throws Exception {
        when(currentUserService.getCurrentUserName()).thenReturn(Optional.of("user1"));
        UserRoleData request = createRequestDao("req-1", "test", "app-1", Role.TESTER, ZonedDateTime.now(), "user1");
        roleRepository.save(request);

        UserRoleServiceImpl service = new UserRoleServiceImpl(kafkaClusters, currentUserService, applicationsService,
                new GalapagosEventManagerMock(), timeService);

        Boolean result = service.cancelUserRoleRequest("req-1", "test").get();

        assertTrue(result);
        assertTrue(roleRepository.getObject("req-1").isEmpty());
    }

    @Test
    void testGetRolesForUser() {
        UserRoleData request1 = new UserRoleData();
        request1.setId("req-1");
        request1.setApplicationId("app-1");
        request1.setUserName("user1");
        request1.setRole(Role.ADMIN);
        request1.setEnvironmentId("test");
        request1.setState(RequestState.APPROVED);
        roleRepository.save(request1);

        UserRoleData request2 = new UserRoleData();
        request2.setId("req-2");
        request2.setApplicationId("app-2");
        request2.setUserName("user1");
        request2.setRole(Role.TESTER);
        request2.setEnvironmentId("test");
        request2.setState(RequestState.APPROVED);
        roleRepository.save(request2);

        UserRoleData request3 = new UserRoleData();
        request2.setId("req-2");
        request2.setApplicationId("app-2");
        request2.setUserName("user1");
        request2.setRole(Role.TESTER);
        request2.setEnvironmentId("test2");
        request2.setState(RequestState.APPROVED);
        roleRepository2.save(request2);

        UserRoleServiceImpl service = new UserRoleServiceImpl(kafkaClusters, currentUserService, applicationsService,
                new GalapagosEventManagerMock(), timeService);

        List<UserRoleData> result = service.getRolesForUser("test", "user1");
        List<UserRoleData> result2 = service.getRolesForUser("test2", "user1");

        assertEquals(2, result.size());
        assertEquals(1, result2.size());
    }

    @Test
    void testDeleteUserRoles() throws Exception {

        UserRoleData request1 = new UserRoleData();
        request1.setId("req-1");
        request1.setApplicationId("app-1");
        request1.setUserName("user1");
        request1.setRole(Role.ADMIN);
        request1.setEnvironmentId("test");
        request1.setState(RequestState.APPROVED);
        roleRepository.save(request1);

        UserRoleData request2 = new UserRoleData();
        request2.setId("req-2");
        request2.setApplicationId("app-1");
        request2.setUserName("user1");
        request2.setRole(Role.TESTER);
        request2.setEnvironmentId("test");
        request2.setState(RequestState.APPROVED);
        roleRepository.save(request2);

        UserRoleServiceImpl service = new UserRoleServiceImpl(kafkaClusters, currentUserService, applicationsService,
                new GalapagosEventManagerMock(), timeService);

        service.deleteUserRoles("test", "user1").get();

        assertEquals(0, service.getRolesForUser("test", "user1").size());
    }

    @Test
    void testGetAllRolesForCurrentUser() {
        when(currentUserService.getCurrentUserName()).thenReturn(Optional.of("user1"));
        UserRoleData request1 = createRequestDao("req-1", "test", "app-1", Role.TESTER, ZonedDateTime.now(), "user1");
        roleRepository.save(request1);
        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("test"));
        UserRoleServiceImpl service = new UserRoleServiceImpl(kafkaClusters, currentUserService, applicationsService,
                new GalapagosEventManagerMock(), timeService);

        Map<String, List<UserRoleData>> result = service.getAllRolesForCurrentUser();

        assertEquals(1, result.size());
        assertTrue(result.containsKey("test"));
        assertEquals(1, result.get("test").size());
        assertEquals("req-1", result.get("test").get(0).getId());
    }

    @Test
    void testDeleteUserRolesId() throws Exception {
        UserRoleData request1 = createRequestDao("req-1", "test", "app-1", Role.TESTER, ZonedDateTime.now(), "user1");
        roleRepository.save(request1);

        UserRoleServiceImpl service = new UserRoleServiceImpl(kafkaClusters, currentUserService, applicationsService,
                new GalapagosEventManagerMock(), timeService);
        assertTrue(roleRepository.getObject("req-1").isPresent());
        service.deleteUserRoleById("test", "req-1").get();
        assertTrue(roleRepository.getObject("req-1").isEmpty());
    }

    @Test
    void testDeleteUserRolesId_nonExistentId() throws Exception {
        UserRoleServiceImpl service = new UserRoleServiceImpl(kafkaClusters, currentUserService, applicationsService,
                new GalapagosEventManagerMock(), timeService);
        service.deleteUserRoleById("test", "req-1").get();
    }

    @Test
    void testDeleteUserRoles_nonExistentEnvironment() {
        UserRoleServiceImpl service = new UserRoleServiceImpl(kafkaClusters, currentUserService, applicationsService,
                new GalapagosEventManagerMock(), timeService);
        CompletableFuture<Void> future = service.deleteUserRoles("nonexistent", "user1");
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(NoSuchElementException.class, executionException.getCause());
        assertEquals("No environment with ID nonexistent found.", executionException.getCause().getMessage());
    }

    @Test
    void testNoUser() {
        when(currentUserService.getCurrentUserName()).thenReturn(Optional.empty());
        UserRoleServiceImpl service = new UserRoleServiceImpl(kafkaClusters, currentUserService, applicationsService,
                new GalapagosEventManagerMock(), timeService);
        CompletableFuture<UserRoleData> future = service.submitRoleRequest("app-1", Role.TESTER, "test", "Test");
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, executionException.getCause());
        assertEquals("A user must be logged in for this operation.", executionException.getCause().getMessage());
    }

    private static UserRoleData createRequestDao(String id, String environmentId, String applicationId, Role role,
            ZonedDateTime createdAt, String userName) {
        UserRoleData dao = new UserRoleData();
        dao.setId(id);
        dao.setApplicationId(applicationId);
        dao.setEnvironmentId(environmentId);
        dao.setRole(role);
        dao.setCreatedAt(createdAt);
        dao.setUserName(userName);
        dao.setState(RequestState.SUBMITTED);
        return dao;
    }
}
