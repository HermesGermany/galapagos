package com.hermesworld.ais.galapagos.ccloud.apiclient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonDeserialize
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceAccountSpec {

    /** The Confluent Cloud resource ID, usually something like sa-xy123 */
    private String resourceId;

    /** The "internal" numeric ID of the service account, currently required for direct ACL updates */
    private String numericId;

    private String description;

    private String displayName;

}
