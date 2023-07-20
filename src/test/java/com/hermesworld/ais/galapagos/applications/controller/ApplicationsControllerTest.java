package com.hermesworld.ais.galapagos.applications.controller;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.staging.StagingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationsControllerTest {

    private ApplicationsService applicationsService;

    private StagingService stagingService;

    private KafkaClusters kafkaClusters;

    @BeforeEach
    void feedMocks() {
        applicationsService = mock(ApplicationsService.class);
        stagingService = mock(StagingService.class);
        kafkaClusters = mock(KafkaClusters.class);
    }

    @Test
    void getRegisteredApplications_knownAppMissing() {
        ApplicationsController controller = new ApplicationsController(applicationsService, stagingService,
                kafkaClusters);

        // WHEN we have one application registered, but not "known"
        ApplicationMetadata existingApp = new ApplicationMetadata();
        existingApp.setApplicationId("ex1");
        ApplicationMetadata nonExistingApp = new ApplicationMetadata();
        nonExistingApp.setApplicationId("nex1");
        List<ApplicationMetadata> metas = List.of(existingApp, nonExistingApp);
        when(applicationsService.getAllApplicationMetadata("test")).thenReturn(metas);

        KnownApplication kapp = mock(KnownApplication.class);
        when(kapp.getName()).thenReturn("Existing App");
        when(kapp.getId()).thenReturn("ex1");
        when(applicationsService.getKnownApplication("ex1")).thenReturn(Optional.of(kapp));
        // nex1 is "unknown"! (e.g. due to errorneous app import)
        when(applicationsService.getKnownApplication("nex1")).thenReturn(Optional.empty());

        List<KnownApplicationDto> regApps = controller.getRegisteredApplications("test");
        assertEquals(1, regApps.size());
        assertEquals("ex1", regApps.get(0).getId());
    }
}
