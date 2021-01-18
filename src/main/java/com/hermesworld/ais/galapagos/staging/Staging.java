package com.hermesworld.ais.galapagos.staging;

import java.util.List;

import com.hermesworld.ais.galapagos.changes.Change;

public interface Staging {

    public String getApplicationId();

    public String getSourceEnvironmentId();

    public String getTargetEnvironmentId();

    public List<Change> getChanges();

    public List<StagingResult> perform();

}
