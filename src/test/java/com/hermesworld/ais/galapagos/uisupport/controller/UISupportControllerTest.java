package com.hermesworld.ais.galapagos.uisupport.controller;

import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class UISupportControllerTest {

    @MockBean
    private KafkaClusters kafkaClusters;

    @Autowired
    private UISupportController testController;

    @Test
    public void testCustomLinks() {
        List<CustomLinkConfig> links = testController.getCustomLinks();
        assertNotNull(links);

        for (int i = 0; i < links.size(); i++) {
            assertNotNull(links.get(i).getId());

            assertNotNull(links.get(i).getHref());
            assertFalse(links.get(i).getHref().isBlank());

            assertNotNull(links.get(i).getLabel());
            assertFalse(links.get(i).getLabel().isBlank());

            assertNotNull(links.get(i).getLinkType());

        }
    }

    @Test
    public void testKafkaDoc() {
        List<KafkaConfigDescriptionDto> result = new UISupportController(null, null, null, null, null, null)
                .getSupportedKafkaConfigs();
        assertNotNull(result);
        assertTrue(result.size() > 10);
        assertTrue(result.stream().filter(d -> d.getConfigDescription().length() > 20).count() > 10);
    }

}
