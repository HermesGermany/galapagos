package com.hermesworld.ais.galapagos.uisupport.controller;

import com.hermesworld.ais.galapagos.GalapagosTestConfig;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(GalapagosTestConfig.class)
class UISupportControllerTest {

    @SuppressWarnings("unused")
    @MockBean
    private KafkaClusters kafkaClusters;

    @Autowired
    private UISupportController testController;

    @Test
    void testCustomLinks() {
        List<CustomLinkConfig> links = testController.getCustomLinks();
        assertNotNull(links);

        for (CustomLinkConfig link : links) {
            assertNotNull(link.getId());

            assertNotNull(link.getHref());
            assertFalse(link.getHref().isBlank());

            assertNotNull(link.getLabel());
            assertFalse(link.getLabel().isBlank());

            assertNotNull(link.getLinkType());
        }
    }

    @Test
    void testKafkaDoc() {
        List<KafkaConfigDescriptionDto> result = new UISupportController(null, null, null, null, null, null, null)
                .getSupportedKafkaConfigs();
        assertNotNull(result);
        assertTrue(result.size() > 10);
        assertTrue(result.stream().filter(d -> d.getConfigDescription().length() > 20).count() > 10);
    }

}
