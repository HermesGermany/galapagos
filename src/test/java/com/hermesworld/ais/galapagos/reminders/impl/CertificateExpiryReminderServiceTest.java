package com.hermesworld.ais.galapagos.reminders.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.reminders.CertificateExpiryReminder;
import com.hermesworld.ais.galapagos.reminders.ReminderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CertificateExpiryReminderServiceTest {

    private KafkaClusters clusters;

    private ApplicationsService applicationsService;

    private CertificateExpiryReminderServiceImpl service;

    private final TopicBasedRepositoryMock<ReminderMetadata> reminderRepository = new TopicBasedRepositoryMock<>();

    @BeforeEach
    public void initClusters() {
        clusters = mock(KafkaClusters.class);
        KafkaCluster cluster = mock(KafkaCluster.class);
        applicationsService = mock(ApplicationsService.class);

        when(cluster.getId()).thenReturn("test");

        when(clusters.getEnvironmentIds()).thenReturn(List.of("test"));
        when(clusters.getEnvironment("test")).thenReturn(Optional.of(cluster));
        when(clusters.getEnvironments()).thenCallRealMethod();

        when(cluster.getRepository("reminders", ReminderMetadata.class)).thenReturn(reminderRepository);

        service = new CertificateExpiryReminderServiceImpl(clusters, applicationsService);
    }

    @Test
    public void testNoReminder() {
        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("123");
        metadata.setCertificateExpiresAt(ZonedDateTime.now().plusDays(365));
        metadata.setDn("CN=abc");

        when(applicationsService.getAllApplicationMetadata("test")).thenReturn(List.of(metadata));

        List<CertificateExpiryReminder> reminders = service.calculateDueCertificateReminders();
        assertTrue(reminders.isEmpty());
    }

    @Test
    public void testSimpleCase() {
        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("123");
        metadata.setCertificateExpiresAt(ZonedDateTime.now().plusDays(40));
        metadata.setDn("CN=abc");

        when(applicationsService.getAllApplicationMetadata("test")).thenReturn(List.of(metadata));

        List<CertificateExpiryReminder> reminders = service.calculateDueCertificateReminders();
        assertEquals(1, reminders.size());
        assertEquals("123", reminders.get(0).getApplicationId());
        assertEquals("test", reminders.get(0).getEnvironmentId());
        assertEquals(ReminderType.THREE_MONTHS, reminders.get(0).getReminderType());
    }

    @Test
    public void testMultipleCallsWithoutMarkMustReturnSameReminders() {
        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("123");
        metadata.setCertificateExpiresAt(ZonedDateTime.now().plusDays(40));
        metadata.setDn("CN=abc");

        when(applicationsService.getAllApplicationMetadata("test")).thenReturn(List.of(metadata));

        List<CertificateExpiryReminder> reminders = service.calculateDueCertificateReminders();
        assertEquals(1, reminders.size());

        // no call to markReminderSentOut - same reminder must still be contained
        reminders = service.calculateDueCertificateReminders();
        assertEquals(1, reminders.size());
    }

    @Test
    public void testSimpleMark() {
        List<ApplicationMetadata> applications = new ArrayList<>();
        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("123");
        metadata.setCertificateExpiresAt(ZonedDateTime.now().plusDays(40));
        metadata.setDn("CN=abc");
        applications.add(metadata);

        metadata = new ApplicationMetadata();
        metadata.setApplicationId("456");
        metadata.setCertificateExpiresAt(ZonedDateTime.now().plusDays(10));
        metadata.setDn("CN=def");
        applications.add(metadata);

        when(applicationsService.getAllApplicationMetadata("test")).thenReturn(applications);

        List<CertificateExpiryReminder> reminders = service.calculateDueCertificateReminders();
        assertEquals(2, reminders.size());

        CertificateExpiryReminder reminder = reminders.get(0);
        service.markReminderSentOut(reminder);

        reminders = service.calculateDueCertificateReminders();
        assertEquals(1, reminders.size());
        assertNotEquals(reminder.getApplicationId(), reminders.get(0).getApplicationId());
    }

    @Test
    public void testShortTimeAlreadySentShouldNotSendLongerTimeReminder() throws Exception {
        List<ApplicationMetadata> applications = new ArrayList<>();
        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("123");
        metadata.setCertificateExpiresAt(ZonedDateTime.now().plusDays(5));
        metadata.setDn("CN=abc");
        applications.add(metadata);

        ReminderMetadata shortReminder = new ReminderMetadata();
        shortReminder.setApplicationId("123");
        shortReminder.setReminderType(ReminderType.ONE_WEEK);
        shortReminder.setReminderId("1");

        reminderRepository.save(shortReminder).get();

        when(applicationsService.getAllApplicationMetadata("test")).thenReturn(applications);

        // no reminder for one month or three months should be sent if one_week has already been sent...
        List<CertificateExpiryReminder> reminders = service.calculateDueCertificateReminders();
        assertEquals(0, reminders.size());
    }

    @Test
    public void testMultipleEnvironmentsWithExpiredEach() {
        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("123");
        metadata.setCertificateExpiresAt(ZonedDateTime.now().plusDays(5));
        metadata.setDn("CN=abc");

        when(applicationsService.getAllApplicationMetadata("test")).thenReturn(List.of(metadata));

        KafkaCluster env2 = mock(KafkaCluster.class);
        when(env2.getId()).thenReturn("test2");
        when(env2.getRepository("reminders", ReminderMetadata.class)).thenReturn(new TopicBasedRepositoryMock<>());
        when(clusters.getEnvironment("test2")).thenReturn(Optional.of(env2));
        when(clusters.getEnvironmentIds()).thenReturn(Arrays.asList("test", "test2"));

        metadata = new ApplicationMetadata();
        metadata.setApplicationId("123");
        metadata.setCertificateExpiresAt(ZonedDateTime.now().plusDays(40));
        metadata.setDn("CN=abc");

        when(applicationsService.getAllApplicationMetadata("test2")).thenReturn(List.of(metadata));

        List<CertificateExpiryReminder> reminders = service.calculateDueCertificateReminders();
        assertEquals(2, reminders.size());

        CertificateExpiryReminder rem1 = reminders.get(0).getEnvironmentId().equals("test") ? reminders.get(0)
                : reminders.get(1);
        CertificateExpiryReminder rem2 = reminders.get(0).getEnvironmentId().equals("test") ? reminders.get(1)
                : reminders.get(0);

        assertEquals("test", rem1.getEnvironmentId());
        assertEquals(ReminderType.ONE_WEEK, rem1.getReminderType());

        assertEquals("test2", rem2.getEnvironmentId());
        assertEquals(ReminderType.THREE_MONTHS, rem2.getReminderType());
    }

    @Test
    public void testMultipleEnvironmentsWithOnlyOneExpired() {
        List<ApplicationMetadata> applications = new ArrayList<>();
        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("123");
        metadata.setCertificateExpiresAt(ZonedDateTime.now().plusDays(5));
        metadata.setDn("CN=abc");
        applications.add(metadata);

        when(applicationsService.getAllApplicationMetadata("test")).thenReturn(applications);

        KafkaCluster env2 = mock(KafkaCluster.class);
        when(env2.getId()).thenReturn("test2");
        when(env2.getRepository("reminders", ReminderMetadata.class)).thenReturn(new TopicBasedRepositoryMock<>());
        when(clusters.getEnvironment("test2")).thenReturn(Optional.of(env2));
        when(clusters.getEnvironmentIds()).thenReturn(Arrays.asList("test", "test2"));

        applications = new ArrayList<>();
        metadata = new ApplicationMetadata();
        metadata.setApplicationId("123");
        metadata.setCertificateExpiresAt(ZonedDateTime.now().plusDays(120));
        metadata.setDn("CN=abc");
        applications.add(metadata);

        when(applicationsService.getAllApplicationMetadata("test2")).thenReturn(applications);

        List<CertificateExpiryReminder> reminders = service.calculateDueCertificateReminders();
        assertEquals(1, reminders.size());

        CertificateExpiryReminder rem = reminders.get(0);

        assertEquals("test", rem.getEnvironmentId());
        assertEquals(ReminderType.ONE_WEEK, rem.getReminderType());
    }

}
