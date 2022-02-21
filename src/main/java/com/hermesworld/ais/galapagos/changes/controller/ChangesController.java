package com.hermesworld.ais.galapagos.changes.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.hermesworld.ais.galapagos.changes.config.GalapagosChangesConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hermesworld.ais.galapagos.changes.ChangeData;
import com.hermesworld.ais.galapagos.changes.ChangesService;

@RestController
public class ChangesController {

    private ChangesService changesService;
    private final GalapagosChangesConfig changesConfig;

    @Autowired
    public ChangesController(ChangesService changesService, GalapagosChangesConfig changesConfig) {
        this.changesService = changesService;
        this.changesConfig = changesConfig;
    }

    @GetMapping(value = "/api/environments/{environmentId}/changelog")
    public List<ChangeData> getChangeLog(@PathVariable String environmentId) {
        // TODO should throw a 404 if invalid environment ID; currently returning empty list then
        return toChangeLog(changesService.getChangeLog(environmentId), this.changesConfig.getEntries(),
                this.changesConfig.getMinDays());
    }

    private List<ChangeData> toChangeLog(List<ChangeData> changes, int limit, int minDays) {
        List<ChangeData> result = new ArrayList<>(limit);
        if (minDays > 0) {
            int i = changes.size() - 1;
            Date threshold = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(minDays));
            while (Date.from(changes.get(i).getTimestamp().toInstant()).after(threshold)) {
                result.add(changes.get(i));
                i--;
            }
        }
        else {
            for (int i = changes.size() - 1; i >= 0 && result.size() < limit; i--) {
                result.add(changes.get(i));
            }
        }
        return result;
    }

}
