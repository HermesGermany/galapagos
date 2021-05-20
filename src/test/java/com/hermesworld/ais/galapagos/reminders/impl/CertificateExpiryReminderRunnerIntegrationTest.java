package com.hermesworld.ais.galapagos.reminders.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.reminders.CertificateExpiryReminder;
import com.hermesworld.ais.galapagos.reminders.CertificateExpiryReminderService;
import com.hermesworld.ais.galapagos.reminders.ReminderType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.mail.MailHealthContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.junit4.SpringRunner;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * This test mainly focuses on the "notification integration" part, i.e., that the mail templates do render correctly
 * and the correct e-mail recipients are determined and passed to the mail engine.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnableAutoConfiguration(exclude = { MailHealthContributorAutoConfiguration.class })
public class CertificateExpiryReminderRunnerIntegrationTest {

    @MockBean
    private KafkaClusters kafkaClusters;

    @MockBean
    private CertificateExpiryReminderService reminderService;

    @MockBean
    private ApplicationsService applicationsService;

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private CertificateExpiryReminderRunner runner;

    private final List<MimeMessage> sentMessages = new ArrayList<>();

    @Before
    public void initMocks() throws MessagingException {
        when(kafkaClusters.getEnvironmentIds()).thenReturn(List.of("test"));

        doAnswer(inv -> sentMessages.add(inv.getArgument(0))).when(mailSender).send((MimeMessage) any());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(null, new ByteArrayInputStream(new byte[0])));

        ApplicationOwnerRequest request = new ApplicationOwnerRequest();
        request.setApplicationId("123");
        request.setNotificationEmailAddress("test@test.com");
        request.setState(RequestState.APPROVED);

        when(applicationsService.getAllApplicationOwnerRequests()).thenReturn(List.of(request));

        ApplicationMetadata metadata = new ApplicationMetadata();
        metadata.setApplicationId("123");
        metadata.setCertificateExpiresAt(ZonedDateTime.of(2020, 11, 10, 10, 20, 0, 0, ZoneId.of("CET")));
        when(applicationsService.getApplicationMetadata("test", "123")).thenReturn(Optional.of(metadata));
    }

    @Test
    public void testSendNotification_threeMonths_success() throws Exception {
        CertificateExpiryReminder reminder = new CertificateExpiryReminder("123", "test", ReminderType.THREE_MONTHS);
        when(reminderService.calculateDueCertificateReminders()).thenReturn(List.of(reminder));

        runner.checkCertificatesForExpiration();

        assertEquals(1, sentMessages.size());
        MimeMessage msg = sentMessages.get(0);
        assertTrue(Arrays.stream(msg.getAllRecipients()).allMatch(addr -> "test@test.com".equals(addr.toString())));
    }

    @Test
    public void testSendNotification_oneMonth_success() throws Exception {
        CertificateExpiryReminder reminder = new CertificateExpiryReminder("123", "test", ReminderType.ONE_MONTH);
        when(reminderService.calculateDueCertificateReminders()).thenReturn(List.of(reminder));

        runner.checkCertificatesForExpiration();

        assertEquals(1, sentMessages.size());
        MimeMessage msg = sentMessages.get(0);
        assertTrue(Arrays.stream(msg.getAllRecipients()).allMatch(addr -> "test@test.com".equals(addr.toString())));
    }

    @Test
    public void testSendNotification_oneWeek_success() throws Exception {
        CertificateExpiryReminder reminder = new CertificateExpiryReminder("123", "test", ReminderType.ONE_WEEK);
        when(reminderService.calculateDueCertificateReminders()).thenReturn(List.of(reminder));

        runner.checkCertificatesForExpiration();

        assertEquals(1, sentMessages.size());
        MimeMessage msg = sentMessages.get(0);
        assertTrue(Arrays.stream(msg.getAllRecipients()).allMatch(addr -> "test@test.com".equals(addr.toString())));
    }

}
