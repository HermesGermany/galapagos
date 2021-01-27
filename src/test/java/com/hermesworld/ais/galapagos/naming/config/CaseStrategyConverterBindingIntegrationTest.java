package com.hermesworld.ais.galapagos.naming.config;

import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test-case-strategies.properties")
public class CaseStrategyConverterBindingIntegrationTest {

    @Autowired
    private NamingConfig config;

    @MockBean
    private KafkaClusters clusters;

    @Test
    public void testConversion() {
        assertEquals(CaseStrategy.PASCAL_CASE, config.getNormalizationStrategy());
    }

}
