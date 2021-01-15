package com.hermesworld.ais.galapagos.security;

/**
 * Types of audit-relevant events provided by Galapagos.
 *
 * @author AlbrechtFlo
 *
 */
public enum GalapagosAuditEventType {

    APPLICATION_OWNER_REQUESTED, APPLICATION_OWNER_REQUEST_CANCELED, APPLICATION_OWNER_REQUEST_UPDATED, TOPIC_CREATED,
    TOPIC_DELETED, TOPIC_UPDATED, TOPIC_SUBSCRIBED, TOPIC_UNSUBSCRIBED, TOPIC_SCHEMA_ADDED,
    APPLICATION_CERTIFICATE_CREATED
}
