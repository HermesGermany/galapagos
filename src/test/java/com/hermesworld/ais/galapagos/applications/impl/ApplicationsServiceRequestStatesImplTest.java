package com.hermesworld.ais.galapagos.applications.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.events.GalapagosEventManager;
import com.hermesworld.ais.galapagos.events.GalapagosEventSink;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.impl.ConnectedKafkaClusters;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.util.TimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ApplicationsServiceRequestStatesImplTest {

    private static final String testUserName = "Alice";

    private static final String testAppId = "1";

    private final GalapagosEventManager eventManager = mock(GalapagosEventManager.class);

    private KafkaClusters kafkaEnvironments;

    private CurrentUserService currentUserService;

    private final TopicBasedRepositoryMock<ApplicationOwnerRequest> repository = new TopicBasedRepositoryMock<>();

    @BeforeEach
    void feedCommonMocks() {
        currentUserService = mock(CurrentUserService.class);
        when(currentUserService.getCurrentUserName()).thenReturn(Optional.of(testUserName));

        KnownApplicationImpl knownApp = new KnownApplicationImpl(testAppId, "App1");

        TopicBasedRepositoryMock<KnownApplicationImpl> appRepository = new TopicBasedRepositoryMock<>();
        appRepository.save(knownApp);

        when(eventManager.newEventSink(any())).thenReturn(mock(GalapagosEventSink.class));
        kafkaEnvironments = mock(ConnectedKafkaClusters.class);
        when(kafkaEnvironments.getGlobalRepository("application-owner-requests", ApplicationOwnerRequest.class))
                .thenReturn(repository);
        when(kafkaEnvironments.getGlobalRepository("known-applications", KnownApplicationImpl.class))
                .thenReturn(appRepository);
    }

    @Test
    public void testFromNothingToSubmitted() throws Exception {
        ApplicationsServiceImpl applicationServiceImpl = new ApplicationsServiceImpl(kafkaEnvironments,
                currentUserService, mock(TimeService.class), mock(NamingService.class), eventManager);
        applicationServiceImpl.submitApplicationOwnerRequest(testAppId, "Moin, bin neu hier.");

        List<ApplicationOwnerRequest> savedRequests = repository.getObjects().stream().collect(Collectors.toList());

        assertEquals(1, savedRequests.size());
        assertNotNull(savedRequests.get(0).getId());
        assertEquals(RequestState.SUBMITTED, savedRequests.get(0).getState());
    }

    @Test
    public void testFromSubmittedToSubmitted() throws Throwable {
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
        ApplicationOwnerRequest applicationOwnerRequest = createRequest(RequestState.SUBMITTED, now);
        repository.save(applicationOwnerRequest);

        ApplicationsServiceImpl applicationServiceImpl = new ApplicationsServiceImpl(kafkaEnvironments,
                currentUserService, mock(TimeService.class), mock(NamingService.class), eventManager);
        try {
            ApplicationOwnerRequest appOwnReq = applicationServiceImpl
                    .submitApplicationOwnerRequest(applicationOwnerRequest.getApplicationId(), "").get();

            assertEquals(RequestState.SUBMITTED, appOwnReq.getState());
            assertEquals(applicationOwnerRequest.getId(), appOwnReq.getId());
        }
        catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void testFromSubmittedToRejected() throws Exception {
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
        ApplicationOwnerRequest applicationOwnerRequest = createRequest(RequestState.SUBMITTED, now);
        repository.save(applicationOwnerRequest);

        ApplicationsServiceImpl appsServImpl = new ApplicationsServiceImpl(kafkaEnvironments, currentUserService,
                mock(TimeService.class), mock(NamingService.class), eventManager);
        appsServImpl.updateApplicationOwnerRequest(applicationOwnerRequest.getId(), RequestState.REJECTED);

        List<ApplicationOwnerRequest> savedRequests = repository.getObjects().stream().collect(Collectors.toList());

        assertEquals(1, savedRequests.size());
        assertEquals(applicationOwnerRequest.getId(), savedRequests.get(0).getId());
        assertEquals(RequestState.REJECTED, savedRequests.get(0).getState());
    }

    @Test
    public void testFromSubmittedToDeletion() throws Exception {
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
        ApplicationOwnerRequest applicationOwnerRequest = createRequest(RequestState.SUBMITTED, now);
        repository.save(applicationOwnerRequest);

        ApplicationsServiceImpl applicationServiceImpl = new ApplicationsServiceImpl(kafkaEnvironments,
                currentUserService, mock(TimeService.class), mock(NamingService.class), eventManager);
        applicationServiceImpl.cancelUserApplicationOwnerRequest(applicationOwnerRequest.getId());

        assertTrue(repository.getObject(applicationOwnerRequest.getId()).isEmpty());
    }

    @Test
    public void testFromSubmittedToApproved() throws Exception {
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
        ApplicationOwnerRequest applicationOwnerRequest = createRequest(RequestState.SUBMITTED, now);
        repository.save(applicationOwnerRequest);

        ApplicationsServiceImpl appsServImpl = new ApplicationsServiceImpl(kafkaEnvironments, currentUserService,
                mock(TimeService.class), mock(NamingService.class), eventManager);
        appsServImpl.updateApplicationOwnerRequest(applicationOwnerRequest.getId(), RequestState.APPROVED);

        List<ApplicationOwnerRequest> savedRequests = repository.getObjects().stream().collect(Collectors.toList());

        assertEquals(1, savedRequests.size());
        assertEquals(applicationOwnerRequest.getId(), savedRequests.get(0).getId());
        assertEquals(RequestState.APPROVED, savedRequests.get(0).getState());
    }

    @Test
    public void testFromApprovedToSubmitted() throws Throwable {
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
        ApplicationOwnerRequest applicationOwnerRequest = createRequest(RequestState.APPROVED, now);
        repository.save(applicationOwnerRequest);

        ApplicationsServiceImpl applicationServiceImpl = new ApplicationsServiceImpl(kafkaEnvironments,
                currentUserService, mock(TimeService.class), mock(NamingService.class), eventManager);
        try {
            ApplicationOwnerRequest appOwnReq = applicationServiceImpl
                    .submitApplicationOwnerRequest(applicationOwnerRequest.getApplicationId(), "").get();

            assertEquals(RequestState.APPROVED, appOwnReq.getState());
            assertEquals(applicationOwnerRequest.getId(), appOwnReq.getId());
        }
        catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void testFromApprovedToResigned() throws Exception {
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
        ApplicationOwnerRequest applicationOwnerRequest = createRequest(RequestState.APPROVED, now);
        repository.save(applicationOwnerRequest);

        ApplicationsServiceImpl applicationServiceImpl = new ApplicationsServiceImpl(kafkaEnvironments,
                currentUserService, mock(TimeService.class), mock(NamingService.class), eventManager);
        applicationServiceImpl.cancelUserApplicationOwnerRequest(applicationOwnerRequest.getId());

        List<ApplicationOwnerRequest> savedRequests = repository.getObjects().stream().collect(Collectors.toList());

        assertEquals(1, savedRequests.size());
        assertEquals(applicationOwnerRequest.getId(), savedRequests.get(0).getId());
        assertEquals(RequestState.RESIGNED, savedRequests.get(0).getState());
    }

    @Test
    public void testFromApprovedToRevoked() throws Exception {
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
        ApplicationOwnerRequest applicationOwnerRequest = createRequest(RequestState.APPROVED, now);
        repository.save(applicationOwnerRequest);

        ApplicationsServiceImpl appsServImpl = new ApplicationsServiceImpl(kafkaEnvironments, currentUserService,
                mock(TimeService.class), mock(NamingService.class), eventManager);
        appsServImpl.updateApplicationOwnerRequest(applicationOwnerRequest.getId(), RequestState.REVOKED);

        List<ApplicationOwnerRequest> savedRequests = repository.getObjects().stream().collect(Collectors.toList());

        assertEquals(1, savedRequests.size());
        assertEquals(applicationOwnerRequest.getId(), savedRequests.get(0).getId());
        assertEquals(RequestState.REVOKED, savedRequests.get(0).getState());
    }

    @Test
    public void testFromResignToSubmit() throws Exception {
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
        ApplicationOwnerRequest applicationOwnerRequest = createRequest(RequestState.RESIGNED, now);
        repository.save(applicationOwnerRequest);

        ApplicationsServiceImpl applicationServiceImpl = new ApplicationsServiceImpl(kafkaEnvironments,
                currentUserService, mock(TimeService.class), mock(NamingService.class), eventManager);
        applicationServiceImpl.submitApplicationOwnerRequest(applicationOwnerRequest.getApplicationId(), "");

        List<ApplicationOwnerRequest> savedRequests = repository.getObjects().stream().collect(Collectors.toList());

        assertEquals(1, savedRequests.size());
        assertEquals(applicationOwnerRequest.getId(), savedRequests.get(0).getId());
        assertEquals(RequestState.SUBMITTED, savedRequests.get(0).getState());
    }

    @Test
    public void testFromRevokedToApproved() throws Exception {
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
        ApplicationOwnerRequest applicationOwnerRequest = createRequest(RequestState.REVOKED, now);
        repository.save(applicationOwnerRequest);

        ApplicationsServiceImpl appsServImpl = new ApplicationsServiceImpl(kafkaEnvironments, currentUserService,
                mock(TimeService.class), mock(NamingService.class), eventManager);
        appsServImpl.updateApplicationOwnerRequest(applicationOwnerRequest.getId(), RequestState.APPROVED);

        List<ApplicationOwnerRequest> savedRequests = repository.getObjects().stream().collect(Collectors.toList());

        assertEquals(1, savedRequests.size());
        assertEquals(applicationOwnerRequest.getId(), savedRequests.get(0).getId());
        assertEquals(RequestState.APPROVED, savedRequests.get(0).getState());
    }

    @Test
    public void testFromRevokedToSubmit() throws Exception {
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneId.systemDefault());
        ApplicationOwnerRequest applicationOwnerRequest = createRequest(RequestState.REVOKED, now);
        repository.save(applicationOwnerRequest);

        ApplicationsServiceImpl applicationServiceImpl = new ApplicationsServiceImpl(kafkaEnvironments,
                currentUserService, mock(TimeService.class), mock(NamingService.class), eventManager);
        applicationServiceImpl.submitApplicationOwnerRequest(applicationOwnerRequest.getApplicationId(), "");

        List<ApplicationOwnerRequest> savedRequests = repository.getObjects().stream().collect(Collectors.toList());

        assertEquals(1, savedRequests.size());
        assertEquals(applicationOwnerRequest.getId(), savedRequests.get(0).getId());
        assertEquals(RequestState.SUBMITTED, savedRequests.get(0).getState());
    }

    @Test
    public void testFromRejectedToApproved() throws Exception {
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
        ApplicationOwnerRequest applicationOwnerRequest = createRequest(RequestState.REJECTED, now);
        repository.save(applicationOwnerRequest);

        ApplicationsServiceImpl appsServImpl = new ApplicationsServiceImpl(kafkaEnvironments, currentUserService,
                mock(TimeService.class), mock(NamingService.class), eventManager);
        appsServImpl.updateApplicationOwnerRequest(applicationOwnerRequest.getId(), RequestState.APPROVED);

        List<ApplicationOwnerRequest> savedRequests = repository.getObjects().stream().collect(Collectors.toList());

        assertEquals(1, savedRequests.size());
        assertEquals(applicationOwnerRequest.getId(), savedRequests.get(0).getId());
        assertEquals(RequestState.APPROVED, savedRequests.get(0).getState());
    }

    @Test
    public void testFromRejectedToSubmitted() throws Exception {
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
        ApplicationOwnerRequest applicationOwnerRequest = createRequest(RequestState.REJECTED, now);
        repository.save(applicationOwnerRequest);

        ApplicationsServiceImpl applicationServiceImpl = new ApplicationsServiceImpl(kafkaEnvironments,
                currentUserService, mock(TimeService.class), mock(NamingService.class), eventManager);
        applicationServiceImpl.submitApplicationOwnerRequest(applicationOwnerRequest.getApplicationId(), "");

        List<ApplicationOwnerRequest> savedRequests = repository.getObjects().stream().collect(Collectors.toList());

        assertEquals(1, savedRequests.size());
        assertEquals(applicationOwnerRequest.getId(), savedRequests.get(0).getId());
        assertEquals(RequestState.SUBMITTED, savedRequests.get(0).getState());
    }

    @Test
    public void testImpossibleTransitionRevoked() throws Throwable {
        assertThrows(IllegalStateException.class, () -> {
            ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
            ApplicationOwnerRequest applicationOwnerRequest = createRequest(RequestState.REVOKED, now);
            repository.save(applicationOwnerRequest);

            ApplicationsServiceImpl appsServImpl = new ApplicationsServiceImpl(kafkaEnvironments, currentUserService,
                    mock(TimeService.class), mock(NamingService.class), eventManager);
            try {
                appsServImpl.cancelUserApplicationOwnerRequest(applicationOwnerRequest.getId()).get();
            }
            catch (ExecutionException e) {
                throw e.getCause();
            }
        });
    }

    @Test
    public void testImpossibleTransitionRejected() throws Throwable {
        assertThrows(IllegalStateException.class, () -> {
            ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
            ApplicationOwnerRequest applicationOwnerRequest = createRequest(RequestState.REJECTED, now);
            repository.save(applicationOwnerRequest);

            ApplicationsServiceImpl appsServImpl = new ApplicationsServiceImpl(kafkaEnvironments, currentUserService,
                    mock(TimeService.class), mock(NamingService.class), eventManager);
            try {
                appsServImpl.cancelUserApplicationOwnerRequest(applicationOwnerRequest.getId()).get();
            }
            catch (ExecutionException e) {
                throw e.getCause();
            }
        });
    }

    @Test
    public void testImpossibleTransitionResigned() throws Throwable {
        assertThrows(IllegalStateException.class, () -> {
            ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 3, 25, 10, 0), ZoneOffset.UTC);
            ApplicationOwnerRequest applicationOwnerRequest = createRequest(RequestState.RESIGNED, now);
            repository.save(applicationOwnerRequest);

            ApplicationsServiceImpl appsServImpl = new ApplicationsServiceImpl(kafkaEnvironments, currentUserService,
                    mock(TimeService.class), mock(NamingService.class), eventManager);
            try {
                appsServImpl.cancelUserApplicationOwnerRequest(applicationOwnerRequest.getId()).get();
            }
            catch (ExecutionException e) {
                throw e.getCause();
            }
        });
    }

    private static ApplicationOwnerRequest createRequest(RequestState reqState, ZonedDateTime createdAt) {
        ApplicationOwnerRequest dao = new ApplicationOwnerRequest();
        dao.setApplicationId(testAppId);
        dao.setCreatedAt(createdAt);
        dao.setId(UUID.randomUUID().toString());
        dao.setUserName(testUserName);
        dao.setState(reqState);
        return dao;
    }
}
