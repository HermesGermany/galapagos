package com.hermesworld.ais.galapagos.adminjobs.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.util.FutureUtil;

class ViewAclsJobTest {

    private ByteArrayOutputStream stdoutData = new ByteArrayOutputStream();

    private PrintStream oldOut;

    @BeforeEach
    void setup() {
        oldOut = System.out;
        System.setOut(new PrintStream(stdoutData));
    }

    @AfterEach
    void cleanup() {
        System.setOut(oldOut);
    }

    @Test
    void testJsonMapping() throws Exception {
        KafkaClusters clusters = mock(KafkaClusters.class);

        KafkaCluster cluster = mock(KafkaCluster.class);
        when(cluster.visitAcls(any())).then(inv -> {
            Function<AclBinding, Boolean> fn = inv.getArgument(0);
            AclBinding binding1 = new AclBinding(new ResourcePattern(ResourceType.GROUP, "group1", PatternType.LITERAL),
                    new AccessControlEntry("dummy", "localhost", AclOperation.ALTER, AclPermissionType.ALLOW));
            AclBinding binding2 = new AclBinding(
                    new ResourcePattern(ResourceType.TOPIC, "topic1", PatternType.PREFIXED),
                    new AccessControlEntry("alice", "otherhost", AclOperation.READ, AclPermissionType.DENY));
            fn.apply(binding1);
            fn.apply(binding2);
            return FutureUtil.noop();
        });

        when(clusters.getEnvironment("test")).thenReturn(Optional.of(cluster));

        ViewAclsJob job = new ViewAclsJob(clusters);

        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getOptionValues("kafka.environment")).thenReturn(List.of("test"));

        job.run(args);

        String stdout = new String(stdoutData.toByteArray());

        assertTrue(stdout.contains("[{\""));
        String jsonData = stdout.substring(stdout.indexOf("[{\""));
        int endIndex = jsonData.indexOf('\r');
        if (endIndex == -1) {
            endIndex = jsonData.indexOf('\n');
        }

        JSONArray arr = new JSONArray(jsonData);
        assertEquals(2, arr.length());

        // only some checks for now
        JSONObject obj1 = arr.getJSONObject(0);
        assertEquals("LITERAL", obj1.getJSONObject("pattern").getString("patternType"));
        assertEquals("localhost", obj1.getJSONObject("entry").getString("host"));

        JSONObject obj2 = arr.getJSONObject(1);
        assertEquals("topic1", obj2.getJSONObject("pattern").getString("name"));
        assertEquals("DENY", obj2.getJSONObject("entry").getString("permissionType"));
    }

}
