package com.hermesworld.ais.galapagos.certificates.reminders.impl;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.certificates.reminders.ReminderType;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.Getter;
import lombok.Setter;

/**
 * Metadata for a certificate expiry reminder which has been sent for a given application ID and reminder type.
 * Instances of this type are stored in the Galapagos Metadata topic <code>reminders</code>.
 */
@JsonSerialize
@Getter
@Setter
public class ReminderMetadata implements HasKey {

    private String reminderId;

    private String applicationId;

    private ReminderType reminderType;

    @Override
    public String key() {
        return reminderId;
    }

}
