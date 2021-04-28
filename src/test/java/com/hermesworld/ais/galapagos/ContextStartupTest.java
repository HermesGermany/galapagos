package com.hermesworld.ais.galapagos;

import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.security.Security;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ContextStartupTest {

    @Autowired
    private ApplicationContext context;

    // mock the KafkaClusters implementation as we do not have a live Kafka server here
    @MockBean
    private KafkaClusters kafkaClusters;

    @BeforeClass
    public static void setupSecurity() {
        Security.setProperty("crypto.policy", "unlimited");
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void testStartupContext() {
        assertNotNull(kafkaClusters);
        assertNotNull(context);
    }

}
