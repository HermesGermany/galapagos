package com.hermesworld.ais.galapagos.devcerts.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.applications.impl.UpdateApplicationAclsListener;
import com.hermesworld.ais.galapagos.devcerts.DevCertificateMetadata;
import com.hermesworld.ais.galapagos.events.ApplicationEvent;
import com.hermesworld.ais.galapagos.events.GalapagosEventContext;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.TimeService;

public class DevUserAclListenerTest {

	private ApplicationsService applicationsService;

	private DevUserAclListener listener;

	private TopicBasedRepositoryMock<DevCertificateMetadata> repository;

	private GalapagosEventContext context;

	private ZonedDateTime timestamp;

	private List<InvocationOnMock> updateAclCalls = new ArrayList<InvocationOnMock>();

	@Before
	public void initMocks() {
		applicationsService = mock(ApplicationsService.class);
		SubscriptionService subscriptionService = mock(SubscriptionService.class);

		timestamp = ZonedDateTime.of(LocalDateTime.of(2020, 10, 5, 10, 0, 0), ZoneOffset.UTC);

		TimeService timeService = new TimeService() {
			@Override
			public ZonedDateTime getTimestamp() {
				return timestamp;
			}
		};

		UpdateApplicationAclsListener aclService = mock(UpdateApplicationAclsListener.class);

		listener = new DevUserAclListener(applicationsService, subscriptionService, timeService, aclService);

		KafkaCluster cluster = mock(KafkaCluster.class);
		when(cluster.updateUserAcls(any())).then(inv -> {
			updateAclCalls.add(inv);
			return FutureUtil.noop();
		});

		repository = new TopicBasedRepositoryMock<>();
		when(cluster.getRepository("devcerts", DevCertificateMetadata.class)).thenReturn(repository);

		context = mock(GalapagosEventContext.class);
		when(context.getKafkaCluster()).thenReturn(cluster);
	}

	@Test
	public void testApplicationRegistered_invalidCertificate() throws Exception {
		DevCertificateMetadata devcert = new DevCertificateMetadata();
		devcert.setCertificateDn("CN=testuser");
		devcert.setUserName("testuser");
		devcert.setExpiryDate(timestamp.minusDays(10).toInstant());
		repository.save(devcert);

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

		assertEquals(0, updateAclCalls.size());
	}

	@Test
	public void testApplicationRegistered() throws Exception {
		DevCertificateMetadata devcert = new DevCertificateMetadata();
		devcert.setCertificateDn("CN=testuser");
		devcert.setUserName("testuser");
		devcert.setExpiryDate(timestamp.plusDays(10).toInstant());
		repository.save(devcert);
		devcert = new DevCertificateMetadata();
		devcert.setCertificateDn("CN=testuser2");
		devcert.setUserName("testuser2");
		devcert.setExpiryDate(timestamp.plusDays(10).toInstant());
		repository.save(devcert);

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

		assertEquals(1, updateAclCalls.size());

		KafkaUser user = updateAclCalls.get(0).getArgument(0);

		assertEquals("User:CN=testuser", user.getKafkaUserName());

	}


}
