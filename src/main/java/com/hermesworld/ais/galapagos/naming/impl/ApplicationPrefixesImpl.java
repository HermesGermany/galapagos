package com.hermesworld.ais.galapagos.naming.impl;

import com.hermesworld.ais.galapagos.naming.ApplicationPrefixes;
import lombok.Getter;
import org.springframework.lang.NonNull;

import java.util.List;

@Getter
public class ApplicationPrefixesImpl implements ApplicationPrefixes {

    private final List<String> internalTopicPrefixes;

    private final List<String> consumerGroupPrefixes;

    private final List<String> transactionIdPrefixes;

    public ApplicationPrefixesImpl(@NonNull List<String> internalTopicPrefixes,
            @NonNull List<String> consumerGroupPrefixes, @NonNull List<String> transactionIdPrefixes) {
        this.internalTopicPrefixes = List.copyOf(internalTopicPrefixes);
        this.consumerGroupPrefixes = List.copyOf(consumerGroupPrefixes);
        this.transactionIdPrefixes = List.copyOf(transactionIdPrefixes);
    }

}
