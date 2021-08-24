package com.hermesworld.ais.galapagos.topics;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class TopicMetadata implements HasKey {

    private String name;

    private TopicType type;

    private String description;

    /**
     * Can be set from external tooling via Kafka Topic galapagos.internal.topics
     */
    private String infoUrl;

    private String ownerApplicationId;

    private boolean isDeprecated;

    private String deprecationText;

    @JsonFormat(shape = Shape.STRING)
    private LocalDate eolDate;

    private boolean subscriptionApprovalRequired;

    private long compactionTimeMillis;

    private long retentionTimeMillis;

    private Criticality criticality;

    private MessagesPerDay messagesPerDay;

    private MessagesSize messagesSize;

    private List<String> producers = Collections.unmodifiableList(new ArrayList<>());

    public TopicMetadata() {
    }

    public TopicMetadata(TopicMetadata original) {
        this.name = original.name;
        this.type = original.type;
        this.description = original.description;
        this.infoUrl = original.infoUrl;
        this.ownerApplicationId = original.ownerApplicationId;
        this.isDeprecated = original.isDeprecated;
        this.deprecationText = original.deprecationText;
        this.eolDate = original.eolDate;
        this.subscriptionApprovalRequired = original.subscriptionApprovalRequired;
        this.compactionTimeMillis = original.compactionTimeMillis;
        this.retentionTimeMillis = original.retentionTimeMillis;
        this.criticality = original.criticality;
        this.messagesPerDay = original.messagesPerDay;
        this.messagesSize = original.messagesSize;
        this.producers = original.producers;
    }

    @Override
    public String key() {
        return name;
    }

}
