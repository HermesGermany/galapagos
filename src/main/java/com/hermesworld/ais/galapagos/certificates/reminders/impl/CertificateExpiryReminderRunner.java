package com.hermesworld.ais.galapagos.certificates.reminders.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.certificates.reminders.CertificateExpiryReminder;
import com.hermesworld.ais.galapagos.certificates.reminders.CertificateExpiryReminderService;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.notifications.NotificationParams;
import com.hermesworld.ais.galapagos.notifications.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Scheduling component which checks every six hours for new certificate expiry notifications to be sent (first check is
 * performed five minutes after application startup).
 */
@Component
@Slf4j
public class CertificateExpiryReminderRunner {

    private final CertificateExpiryReminderService reminderService;

    private final NotificationService notificationService;

    private final ApplicationsService applicationsService;

    private final KafkaClusters kafkaClusters;

    @Autowired
    public CertificateExpiryReminderRunner(CertificateExpiryReminderService reminderService,
            NotificationService notificationService, ApplicationsService applicationsService,
            KafkaClusters kafkaClusters) {
        this.reminderService = reminderService;
        this.notificationService = notificationService;
        this.applicationsService = applicationsService;
        this.kafkaClusters = kafkaClusters;
    }

    /**
     * Check for new certificate expiry notifications to be sent. Is called by Spring Scheduler.
     */
    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT6H")
    public void checkCertificatesForExpiration() {
        log.info("Checking for soon expiring certificates...");
        List<CertificateExpiryReminder> reminders = reminderService.calculateDueCertificateReminders();
        log.info("Found {} certificate expiry reminder(s) to be sent.", reminders.size());

        for (CertificateExpiryReminder reminder : reminders) {
            try {
                sendReminderEmail(reminder).thenAccept(v -> reminderService.markReminderSentOut(reminder)).get();
            }
            catch (ExecutionException e) {
                log.error("Could not send out certificate expiration reminder e-mail for application "
                        + reminder.getApplicationId(), e.getCause());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private CompletableFuture<Void> sendReminderEmail(CertificateExpiryReminder reminder) {
        NotificationParams params = new NotificationParams(
                "certificate-reminder-" + reminder.getReminderType().name().toLowerCase(Locale.US));
        params.addVariable("app_name", getApplicationName(reminder.getApplicationId()));
        params.addVariable("expiry_date", getDateOfExpiry(reminder.getApplicationId(), reminder.getEnvironmentId()));
        params.addVariable("environment_name", kafkaClusters.getEnvironmentMetadata(reminder.getEnvironmentId())
                .map(env -> env.getName()).orElse(reminder.getEnvironmentId()));

        return notificationService.notifyApplicationTopicOwners(reminder.getApplicationId(), params);
    }

    private String getApplicationName(String applicationId) {
        return applicationsService.getKnownApplications(false).stream().filter(app -> applicationId.equals(app.getId()))
                .map(app -> app.getName()).findAny().orElse("(Unknown application)");
    }

    private ZonedDateTime getDateOfExpiry(String applicationId, String environmentId) {
        return applicationsService.getApplicationMetadata(environmentId, applicationId)
                .map(meta -> meta.getCertificateExpiresAt()).orElse(null);
    }

}
