package com.hermesworld.ais.galapagos.certificates.reminders.impl;

import com.hermesworld.ais.galapagos.GalapagosTestConfig;
import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationConfig;
import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationModule;
import com.hermesworld.ais.galapagos.certificates.reminders.CertificateExpiryReminder;
import com.hermesworld.ais.galapagos.certificates.reminders.CertificateExpiryReminderService;
import com.hermesworld.ais.galapagos.certificates.reminders.ReminderType;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.mail.MailHealthContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayInputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * This test mainly focuses on the "notification integration" part, i.e., that the mail templates do render correctly
 * and the correct e-mail recipients are determined and passed to the mail engine.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnableAutoConfiguration(exclude = { MailHealthContributorAutoConfiguration.class })
@Import(GalapagosTestConfig.class)
class CertificateExpiryReminderRunnerIntegrationTest {

    @MockBean
    private KafkaClusters kafkaClusters;

    @MockBean
    private CertificateExpiryReminderService reminderService;

    @MockBean
    private ApplicationsService applicationsService;

    @MockBean
    private JavaMailSender mailSender;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private CertificateExpiryReminderRunner runner;

    private final List<MimeMessage> sentMessages = new ArrayList<>();

    @BeforeEach
    void initMocks() throws MessagingException {
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
        metadata.setAuthenticationJson(new JSONObject(Map.of("expiresAt", "2020-11-10T10:20:00.000Z")).toString());
        when(applicationsService.getApplicationMetadata("test", "123")).thenReturn(Optional.of(metadata));

        when(kafkaClusters.getAuthenticationModule("test")).thenReturn(
                Optional.of(new CertificatesAuthenticationModule("test", new CertificatesAuthenticationConfig())));
    }

    @Test
    void testSendNotification_threeMonths_success() throws Exception {
        CertificateExpiryReminder reminder = new CertificateExpiryReminder("123", "test", ReminderType.THREE_MONTHS);
        when(reminderService.calculateDueCertificateReminders()).thenReturn(List.of(reminder));

        runner.checkCertificatesForExpiration();

        assertEquals(1, sentMessages.size());
        MimeMessage msg = sentMessages.get(0);
        assertTrue(Arrays.stream(msg.getAllRecipients()).allMatch(addr -> "test@test.com".equals(addr.toString())));
    }

    @Test
    void testSendNotification_oneMonth_success() throws Exception {
        CertificateExpiryReminder reminder = new CertificateExpiryReminder("123", "test", ReminderType.ONE_MONTH);
        when(reminderService.calculateDueCertificateReminders()).thenReturn(List.of(reminder));

        runner.checkCertificatesForExpiration();

        assertEquals(1, sentMessages.size());
        MimeMessage msg = sentMessages.get(0);
        assertTrue(Arrays.stream(msg.getAllRecipients()).allMatch(addr -> "test@test.com".equals(addr.toString())));
    }

    @Test
    void testSendNotification_oneWeek_success() throws Exception {
        CertificateExpiryReminder reminder = new CertificateExpiryReminder("123", "test", ReminderType.ONE_WEEK);
        when(reminderService.calculateDueCertificateReminders()).thenReturn(List.of(reminder));

        runner.checkCertificatesForExpiration();

        assertEquals(1, sentMessages.size());
        MimeMessage msg = sentMessages.get(0);
        assertTrue(Arrays.stream(msg.getAllRecipients()).allMatch(addr -> "test@test.com".equals(addr.toString())));
    }

}
