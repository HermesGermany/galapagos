package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.util.AclSupport;
import org.apache.kafka.common.acl.AclBinding;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.util.StringUtils;

import java.util.Collection;

class ToolingUser implements KafkaUser {

    private final ApplicationMetadata metadata;

    private final String environmentId;

    private final KafkaAuthenticationModule authenticationModule;

    private final AclSupport aclSupport;

    public ToolingUser(ApplicationMetadata metadata, String environmentId,
            KafkaAuthenticationModule authenticationModule, AclSupport aclSupport) {
        this.metadata = metadata;
        this.environmentId = environmentId;
        this.authenticationModule = authenticationModule;
        this.aclSupport = aclSupport;
    }

    @Override
    public String getKafkaUserName() {
        if (!StringUtils.hasLength(metadata.getAuthenticationJson())) {
            throw new JSONException("No authentication JSON stored for application " + metadata.getApplicationId());
        }
        return authenticationModule.extractKafkaUserName(new JSONObject(metadata.getAuthenticationJson()));
    }

    @Override
    public Collection<AclBinding> getRequiredAclBindings() {
        return aclSupport.getRequiredAclBindings(environmentId, metadata, getKafkaUserName(), false);
    }
}
