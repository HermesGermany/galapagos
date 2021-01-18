package com.hermesworld.ais.galapagos.reminders;

import java.util.List;

/**
 * A service which is able to calculate the due reminders for application topic owners about the expiry of one of their
 * certificates. The service ensures that one reminder is reported to be sent (each), when
 * <ul>
 * <li>the certificate expires in less than three months</li>,
 * <li>the certificate expires in less than one month</li>,
 * <li>the certificate expires in less than one week.</li>
 * </ul>
 * As soon as a reminder is reported to have been sent, the reminder will not be reported again for the same expiry
 * period (even after restarts of Galapagos).
 *
 * @author AlbrechtFlo
 * @author SoltaniFaz
 */
public interface CertificateExpiryReminderService {

    /**
     * Calculates for which application certificates a reminder is currently due (and has not been sent out yet).
     *
     * @return A list of reminders which should be sent out by the caller. Reminders may only occur in this list until
     *         they have been marked as sent out by a call to {@link #markReminderSentOut(CertificateExpiryReminder)}.
     */
    List<CertificateExpiryReminder> calculateDueCertificateReminders();

    /**
     * Informs the repository that a reminder (which has been returned by {@link #calculateDueCertificateReminders()})
     * has been sent out successfully and should no longer be returned as being due.
     *
     * @param reminder Reminder to mark as sent out.
     */
    void markReminderSentOut(CertificateExpiryReminder reminder);

}
