package com.hermesworld.ais.galapagos.certificates.reminders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Types of certificate expiry reminders which are sent. Each type can be queried for the number of days before
 * certificate expiry a reminder of this type should be sent.
 */
public enum ReminderType {

    ONE_WEEK(7), ONE_MONTH(30), THREE_MONTHS(90);

    private final int daysToAdd;

    /**
     * Constructs a new enum value which adds the given amount of days in its {@link #calculateInstant(Instant)} method.
     *
     * @param daysToAdd Days to add when {@link #calculateInstant(Instant)} is called.
     */
    ReminderType(int daysToAdd) {
        this.daysToAdd = daysToAdd;
    }

    /**
     * Adds an amount of days to the given instant and returns the resulting instant.
     *
     * @param now Instant to add days to.
     *
     * @return Instant with days added to the given instant.
     */
    public Instant calculateInstant(Instant now) {
        return now.plus(this.daysToAdd, ChronoUnit.DAYS);
    }

}
