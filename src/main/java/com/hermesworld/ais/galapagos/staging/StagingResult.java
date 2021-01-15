package com.hermesworld.ais.galapagos.staging;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.changes.Change;
import lombok.Getter;

@JsonSerialize
@Getter
public final class StagingResult {

    private Change change;

    private boolean stagingSuccessful;

    private String errorMessage;

    public StagingResult(Change change, boolean stagingSuccessful, String errorMessage) {
        this.change = change;
        this.stagingSuccessful = stagingSuccessful;
        this.errorMessage = errorMessage;
    }

}
