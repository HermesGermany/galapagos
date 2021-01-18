package com.hermesworld.ais.galapagos.applications;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonSerialize
public class ApplicationOwnerRequest implements HasKey {

    private String id;

    private String applicationId;

    private String userName;

    private String comments;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private ZonedDateTime createdAt;

    private String notificationEmailAddress;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private ZonedDateTime lastStatusChangeAt;

    private String lastStatusChangeBy;

    private RequestState state;

    @Override
    public String key() {
        return id;
    }

}
