package com.hermesworld.ais.galapagos.kafka;

/**
 * Represents a single configuration for a topic. Is more or less equivalent to Kafka's <code>ConfigEntry</code> class,
 * but here for decoupling Galapagos API from Kafka AdminClient API.
 *
 * @author AlbrechtFlo
 *
 */
public interface TopicConfigEntry {

    String getName();

    String getValue();

    boolean isDefault();

    boolean isReadOnly();

    boolean isSensitive();

}
