package com.hermesworld.ais.galapagos.topics;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@JsonSerialize
@Getter
@Setter
public class SchemaMetadata implements HasKey {

    private String id;

    private String topicName;

    private int schemaVersion;

    private String jsonSchema;

    private ZonedDateTime createdAt;

    private String createdBy;

    private String changeDescription;

    public SchemaMetadata() {
    }

    public SchemaMetadata(SchemaMetadata original) {
        this.id = original.id;
        this.topicName = original.topicName;
        this.schemaVersion = original.schemaVersion;
        this.jsonSchema = original.jsonSchema;
        this.createdAt = original.createdAt;
        this.createdBy = original.createdBy;
        this.changeDescription = original.changeDescription;
    }

    @Override
    public String key() {
        return id;
    }

}
