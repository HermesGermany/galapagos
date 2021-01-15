package com.hermesworld.ais.galapagos.subscriptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.Getter;
import lombok.Setter;

/**
 * Descriptor of a "Subscription". A subscription is the logical "right" for an application to read from a Kafka Topic
 * (or, in case of <code>COMMAND</code> topics, write to that topic). Each subscription instance exists on only one
 * Kafka cluster, so an application may be subscribed to a topic on one cluster, but not on another. <br>
 * Subscriptions have a <i>state</i>, which usually is <code>APPROVED</code>. In case of topics for which the
 * <code>requiresSubscriptionApproval</code> flag is set, this state is initially <code>PENDING</code>, and, after
 * handling by the owner of the subscribed topic, is updated to <code>APPROVED</code> or <code>REJECTED</code>,
 * respectively. <br>
 * Additionally, users can specify a description / a reasoning for a subscription. This can be used for security audits
 * and / or synchronizations in external architecture tools. <br>
 * Subscriptions are a completely virtual construct only existing in Galapagos. They map best to the ACLs in Kafka which
 * are created so the subscribing applications can read from the desired topics (but note that these ACLs are not
 * created by the Subscriptions Service, but by the class <code>UpdateApplicationAclsListener</code>).
 *
 * @author AlbrechtFlo
 */
@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriptionMetadata implements HasKey {

    @Getter
    @Setter
    private String id;

    @Getter
    @Setter
    private String clientApplicationId;

    @Getter
    @Setter
    private String topicName;

    /**
     * For backwards compatibility with old metadata, this defaults to APPROVED.
     */
    @Setter
    private SubscriptionState state = SubscriptionState.APPROVED;

    @Getter
    @Setter
    private String description;

    public SubscriptionMetadata() {
    }

    public SubscriptionMetadata(SubscriptionMetadata original) {
        id = original.id;
        clientApplicationId = original.clientApplicationId;
        topicName = original.topicName;
        state = original.state;
        description = original.description;
    }

    @Override
    public String key() {
        return id;
    }

    public SubscriptionState getState() {
        return state == null ? SubscriptionState.APPROVED : state;
    }

}
