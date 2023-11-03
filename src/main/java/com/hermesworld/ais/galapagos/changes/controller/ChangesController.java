package com.hermesworld.ais.galapagos.changes.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hermesworld.ais.galapagos.changes.ChangeData;
import com.hermesworld.ais.galapagos.changes.ChangesService;

@RestController
public class ChangesController {

    private ChangesService changesService;

    public ChangesController(ChangesService changesService) {
        this.changesService = changesService;
    }

    @GetMapping(value = "/api/environments/{environmentId}/changelog")
    public List<ChangeData> getChangeLog(@PathVariable String environmentId,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        // TODO should throw a 404 if invalid environment ID; currently returning empty list then
        return toChangeLog(changesService.getChangeLog(environmentId), limit);
    }

    private List<ChangeData> toChangeLog(List<ChangeData> changes, int limit) {
        List<ChangeData> result = new ArrayList<>(limit);
        for (int i = changes.size() - 1; i >= 0 && result.size() < limit; i--) {
            result.add(changes.get(i));
        }
        return result;
    }

}
