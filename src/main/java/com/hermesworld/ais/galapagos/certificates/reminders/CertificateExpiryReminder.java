package com.hermesworld.ais.galapagos.certificates.reminders;

import lombok.Getter;

/**
 * Represents a reminder about the expiration of a certificate which should be sent out.
 *
 * @author AlbrechtFlo
 */
@Getter
public final class CertificateExpiryReminder {

    private final String applicationId;

    private final String environmentId;

    private final ReminderType reminderType;

    /**
     * Constructs a new reminder instance.
     *
     * @param applicationId The ID of the application this reminder is for.
     * @param environmentId The ID of the environment for which this certificate is valid.
     * @param reminderType  The type of the reminder, e.g. three-months-reminder.
     */
    public CertificateExpiryReminder(String applicationId, String environmentId, ReminderType reminderType) {
        this.applicationId = applicationId;
        this.environmentId = environmentId;
        this.reminderType = reminderType;
    }

}
