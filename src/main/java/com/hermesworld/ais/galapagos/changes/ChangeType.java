package com.hermesworld.ais.galapagos.changes;

public enum ChangeType {

    TOPIC_CREATED, TOPIC_SUBSCRIBED, TOPIC_UNSUBSCRIBED, TOPIC_SUBSCRIPTION_UPDATED, TOPIC_DELETED,
    TOPIC_DESCRIPTION_CHANGED, TOPIC_DEPRECATED, TOPIC_UNDEPRECATED, TOPIC_SUBSCRIPTION_APPROVAL_REQUIRED_FLAG_UPDATED,
    TOPIC_SCHEMA_VERSION_PUBLISHED, TOPIC_PRODUCER_APPLICATION_ADDED, TOPIC_PRODUCER_APPLICATION_REMOVED,
    TOPIC_OWNER_CHANGED,

    /**
     * A change which consists of multiple changes. This is only used during Staging and will not be serialized to the
     * changelog.
     */
    COMPOUND_CHANGE,

    /**
     * @deprecated No longer used as of Galapagos 0.3.0.
     */
    @Deprecated
    APPLICATION_REGISTERED,

    /**
     * @deprecated As users can change configurations of topics via command-line as well, this change type is a little
     *             bit confusing. Beginning with Galapagos 0.2.0, Topic configuration is treated as something outside
     *             the scope of standard Galapagos model, and extra screens allow for direct editing of the
     *             configuration stored in Kafka (as users could also do using Kafka command line tools).
     */
    @Deprecated
    TOPIC_CONFIG_UPDATED

}
