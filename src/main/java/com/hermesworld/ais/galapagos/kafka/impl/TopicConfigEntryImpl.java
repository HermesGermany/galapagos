package com.hermesworld.ais.galapagos.kafka.impl;

import org.apache.kafka.clients.admin.ConfigEntry;

import com.hermesworld.ais.galapagos.kafka.TopicConfigEntry;

/**
 * Implementation of the {@link TopicConfigEntry} interface which just wraps a Kafka {@link ConfigEntry} object and
 * delegates all calls to the wrapped object.
 *
 * @author AlbrechtFlo
 *
 */
final class TopicConfigEntryImpl implements TopicConfigEntry {

    private ConfigEntry entry;

    public TopicConfigEntryImpl(ConfigEntry kafkaConfigEntry) {
        this.entry = kafkaConfigEntry;
    }

    @Override
    public String getName() {
        return entry.name();
    }

    @Override
    public String getValue() {
        return entry.value();
    }

    @Override
    public boolean isDefault() {
        return entry.isDefault();
    }

    @Override
    public boolean isSensitive() {
        return entry.isSensitive();
    }

    @Override
    public boolean isReadOnly() {
        return entry.isReadOnly();
    }

}
