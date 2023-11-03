package com.hermesworld.ais.galapagos.devauth.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationConfig;
import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationModule;
import com.hermesworld.ais.galapagos.devauth.DevAuthenticationMetadata;
import com.hermesworld.ais.galapagos.events.ApplicationEvent;
import com.hermesworld.ais.galapagos.events.GalapagosEventContext;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.kafka.util.AclSupport;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.TimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DevUserAclListenerTest {

    @Mock
    private ApplicationsService applicationsService;

    @Mock
    private KafkaCluster cluster;

    @Mock
    private GalapagosEventContext context;

    private DevUserAclListener listener;

    private KafkaClusters clusters;

    private AclSupport aclSupport;

    private TopicBasedRepositoryMock<DevAuthenticationMetadata> repository;

    private ZonedDateTime timestamp;

    @BeforeEach
    void initMocks() {
        MockitoAnnotations.openMocks(this);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        timestamp = ZonedDateTime.of(LocalDateTime.of(2020, 10, 5, 10, 0, 0), ZoneOffset.UTC);
        TimeService timeService = () -> timestamp;

        aclSupport = mock(AclSupport.class);
        clusters = mock(KafkaClusters.class);
        when(clusters.getAuthenticationModule(any())).thenReturn(Optional
                .of(new CertificatesAuthenticationModule("test", mock(CertificatesAuthenticationConfig.class))));
        listener = new DevUserAclListener(applicationsService, subscriptionService, timeService, aclSupport, clusters);

        repository = new TopicBasedRepositoryMock<>();
        when(cluster.getRepository("devauth", DevAuthenticationMetadata.class)).thenReturn(repository);

        context = mock(GalapagosEventContext.class);
        when(context.getKafkaCluster()).thenReturn(cluster);
        when(cluster.updateUserAcls(any())).thenReturn(FutureUtil.noop());
    }

    @Test
    void testApplicationRegistered_invalidCertificate() throws Exception {
        DevAuthenticationMetadata devAuth = new DevAuthenticationMetadata();
        devAuth.setUserName("testuser");
        devAuth.setAuthenticationJson("{\"expiresAt\":\"2017-02-03T10:37:30Z\"}");
        repository.save(devAuth).get();

        List<ApplicationOwnerRequest> requests = new ArrayList<>();
        ApplicationOwnerRequest request = new ApplicationOwnerRequest();
        request.setId("1");
        request.setApplicationId("test123");
        request.setUserName("testuser");
        request.setState(RequestState.APPROVED);
        requests.add(request);

        when(applicationsService.getAllApplicationOwnerRequests()).thenReturn(requests);

        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("test123");
        ApplicationEvent event = new ApplicationEvent(context, metadata);

        listener.handleApplicationRegistered(event).get();

        verify(cluster, times(0)).updateUserAcls(any());
    }

    @Test
    void testApplicationRegistered() throws Exception {
        DevAuthenticationMetadata devAuth = new DevAuthenticationMetadata();
        devAuth.setUserName("testuser");
        devAuth.setAuthenticationJson(
                "{\"expiresAt\":\"" + timestamp.plusDays(10).toInstant().toString() + "\",\"dn\":\"CN=testuser\"}");
        repository.save(devAuth).get();
        devAuth = new DevAuthenticationMetadata();
        devAuth.setUserName("testuser2");
        devAuth.setAuthenticationJson(
                "{\"expiresAt\":\"" + timestamp.plusDays(10).toInstant().toString() + "\",\"dn\":\"CN=testuser2\"}");
        repository.save(devAuth).get();

        List<ApplicationOwnerRequest> requests = new ArrayList<>();
        ApplicationOwnerRequest request = new ApplicationOwnerRequest();
        request.setId("1");
        request.setApplicationId("test123");
        request.setUserName("testuser");
        request.setState(RequestState.APPROVED);
        requests.add(request);

        when(applicationsService.getAllApplicationOwnerRequests()).thenReturn(requests);

        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("test123");
        ApplicationEvent event = new ApplicationEvent(context, metadata);

        listener.handleApplicationRegistered(event).get();

        ArgumentCaptor<KafkaUser> userCaptor = ArgumentCaptor.forClass(KafkaUser.class);
        verify(cluster, times(1)).updateUserAcls(userCaptor.capture());

        KafkaUser user = userCaptor.getValue();
        assertEquals("User:CN=testuser", user.getKafkaUserName());
    }

    @Test
    void testWriteAccessFlag() throws Exception {
        KafkaEnvironmentConfig config = mock(KafkaEnvironmentConfig.class);
        when(config.isDeveloperWriteAccess()).thenReturn(true);
        when(cluster.getId()).thenReturn("test");
        when(clusters.getEnvironmentMetadata("test")).thenReturn(Optional.of(config));

        DevAuthenticationMetadata metadata = new DevAuthenticationMetadata();
        metadata.setUserName("user123");
        metadata.setAuthenticationJson("{\"dn\":\"CN=testuser\"}");

        ApplicationOwnerRequest request1 = new ApplicationOwnerRequest();
        request1.setUserName("user123");
        request1.setState(RequestState.APPROVED);
        request1.setApplicationId("app-1");
        ApplicationOwnerRequest request2 = new ApplicationOwnerRequest();
        request2.setUserName("user123");
        request2.setState(RequestState.SUBMITTED);
        request2.setApplicationId("app-2");

        ApplicationMetadata app1 = new ApplicationMetadata();
        app1.setApplicationId("app-1");
        ApplicationMetadata app2 = new ApplicationMetadata();
        app1.setApplicationId("app-2");

        when(applicationsService.getAllApplicationOwnerRequests()).thenReturn(List.of(request1, request2));
        when(applicationsService.getApplicationMetadata("test", "app-1")).thenReturn(Optional.of(app1));
        when(applicationsService.getApplicationMetadata("test", "app-2")).thenReturn(Optional.of(app2));

        when(aclSupport.getRequiredAclBindings("test", app1, "User:CN=testuser", false)).thenReturn(List.of());
        when(aclSupport.getRequiredAclBindings("test", app2, "User:CN=testuser", false))
                .thenThrow(new RuntimeException("No ACLs for app2 should be assigned"));

        listener.updateAcls(cluster, Set.of(metadata)).get();
        ArgumentCaptor<KafkaUser> userCaptor = ArgumentCaptor.forClass(KafkaUser.class);
        verify(cluster, times(1)).updateUserAcls(userCaptor.capture());

        userCaptor.getValue().getRequiredAclBindings();
        verify(aclSupport, times(1)).getRequiredAclBindings("test", app1, "User:CN=testuser", false);
        verify(aclSupport, times(0)).getRequiredAclBindings("test", app1, "User:CN=testuser", true);
    }

}
