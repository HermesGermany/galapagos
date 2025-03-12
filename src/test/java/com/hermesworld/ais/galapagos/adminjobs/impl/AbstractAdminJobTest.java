package com.hermesworld.ais.galapagos.adminjobs.impl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbstractAdminJobTest {

    @Test
    public void testBanner() {
        AbstractAdminJob job = new AbstractAdminJob() {
            @Override
            public String getJobName() {
                return "test-job";
            }

            @Override
            public void run(ApplicationArguments allArguments) {
            }
        };
        assertEquals("===== This is a banner =====", job.banner("This is a banner", 28));
        assertEquals("=== Short Banner ===", job.banner("Short Banner", 10));
        assertEquals("==== Imbalance =====", job.banner("Imbalance", 20));
        assertEquals("=================", job.banner("", 17));
    }

}