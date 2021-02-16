package com.hermesworld.ais.galapagos.applications.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import org.springframework.lang.NonNull;

import java.util.List;

@JsonSerialize
@Getter
public class ApplicationPrefixesDto {

    private final List<String> internalTopicPrefixes;

    private final List<String> consumerGroupPrefixes;

    private final List<String> transactionIdPrefixes;

    public ApplicationPrefixesDto(@NonNull List<String> internalTopicPrefixes,
            @NonNull List<String> consumerGroupPrefixes, @NonNull List<String> transactionIdPrefixes) {
        this.internalTopicPrefixes = List.copyOf(internalTopicPrefixes);
        this.consumerGroupPrefixes = List.copyOf(consumerGroupPrefixes);
        this.transactionIdPrefixes = List.copyOf(transactionIdPrefixes);
    }

}
