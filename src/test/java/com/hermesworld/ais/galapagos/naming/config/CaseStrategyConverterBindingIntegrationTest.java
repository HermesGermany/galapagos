package com.hermesworld.ais.galapagos.naming.config;

import com.hermesworld.ais.galapagos.GalapagosTestConfig;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(locations = "classpath:test-case-strategies.properties")
@Import(GalapagosTestConfig.class)
class CaseStrategyConverterBindingIntegrationTest {

    @Autowired
    private NamingConfig config;

    @SuppressWarnings("unused")
    @MockBean
    private KafkaClusters clusters;

    @Test
    void testConversion() {
        assertEquals(CaseStrategy.PASCAL_CASE, config.getNormalizationStrategy());
    }

}
