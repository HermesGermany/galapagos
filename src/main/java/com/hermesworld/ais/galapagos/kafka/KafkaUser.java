package com.hermesworld.ais.galapagos.kafka;

import java.util.Collection;

import org.apache.kafka.common.acl.AclBinding;

public interface KafkaUser {

    String getKafkaUserName();

    Collection<AclBinding> getRequiredAclBindings();

}
