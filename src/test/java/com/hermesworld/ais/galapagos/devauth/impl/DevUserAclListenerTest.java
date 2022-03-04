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
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.kafka.util.AclSupport;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.TimeService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DevUserAclListenerTest {

    private ApplicationsService applicationsService;

    private DevUserAclListener listener;

    private TopicBasedRepositoryMock<DevAuthenticationMetadata> repository;

    private GalapagosEventContext context;

    private ZonedDateTime timestamp;

    private final List<InvocationOnMock> updateAclCalls = new ArrayList<>();

    @BeforeEach
    public void initMocks() {
        applicationsService = mock(ApplicationsService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        timestamp = ZonedDateTime.of(LocalDateTime.of(2020, 10, 5, 10, 0, 0), ZoneOffset.UTC);
        TimeService timeService = () -> timestamp;

        AclSupport aclSupport = mock(AclSupport.class);

        KafkaClusters clusters = mock(KafkaClusters.class);
        when(clusters.getAuthenticationModule(any())).thenReturn(Optional
                .of(new CertificatesAuthenticationModule("test", mock(CertificatesAuthenticationConfig.class))));
        listener = new DevUserAclListener(applicationsService, subscriptionService, timeService, aclSupport, clusters);

        KafkaCluster cluster = mock(KafkaCluster.class);
        when(cluster.updateUserAcls(any())).then(inv -> {
            updateAclCalls.add(inv);
            return FutureUtil.noop();
        });

        repository = new TopicBasedRepositoryMock<>();
        when(cluster.getRepository("devauth", DevAuthenticationMetadata.class)).thenReturn(repository);

        context = mock(GalapagosEventContext.class);
        when(context.getKafkaCluster()).thenReturn(cluster);
    }

    @Test
    public void testApplicationRegistered_invalidCertificate() throws Exception {
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

        Assertions.assertEquals(0, updateAclCalls.size());
    }

    @Test
    public void testApplicationRegistered() throws Exception {
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

        Assertions.assertEquals(1, updateAclCalls.size());

        KafkaUser user = updateAclCalls.get(0).getArgument(0);

        Assertions.assertEquals("User:CN=testuser", user.getKafkaUserName());
    }

}
