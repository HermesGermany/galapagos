package com.hermesworld.ais.galapagos.applications;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.naming.ApplicationPrefixes;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationMetadata implements HasKey, ApplicationPrefixes {

    private String applicationId;

    private List<String> consumerGroupPrefixes = new ArrayList<>();

    private List<String> internalTopicPrefixes = new ArrayList<>();

    private List<String> transactionIdPrefixes = new ArrayList<>();

    private String authenticationJson;

    public ApplicationMetadata() {
    }

    public ApplicationMetadata(ApplicationMetadata original) {
        this.applicationId = original.applicationId;
        this.consumerGroupPrefixes = List.copyOf(original.consumerGroupPrefixes);
        this.internalTopicPrefixes = List.copyOf(original.consumerGroupPrefixes);
        this.transactionIdPrefixes = List.copyOf(original.transactionIdPrefixes);
        this.authenticationJson = original.authenticationJson;
    }

    @Override
    public String key() {
        return applicationId;
    }

}
