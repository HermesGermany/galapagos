package com.hermesworld.ais.galapagos.kafka.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourceType;

@Getter
@Setter
public class DefaultAclConfig {

    private String name;

    private ResourceType resourceType;

    private PatternType patternType;

    private AclOperation operation;

}
