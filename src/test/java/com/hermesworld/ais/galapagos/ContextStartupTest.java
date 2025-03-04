package com.hermesworld.ais.galapagos;

import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.security.Security;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Import(GalapagosTestConfig.class)
class ContextStartupTest {

    @Autowired
    private ApplicationContext context;

    // mock the KafkaClusters implementation as we do not have a live Kafka server here
    @MockitoBean
    private KafkaClusters kafkaClusters;

    @BeforeAll
    static void setupSecurity() {
        Security.setProperty("crypto.policy", "unlimited");
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void testStartupContext() {
        assertNotNull(kafkaClusters);
        assertNotNull(context);
    }

}
