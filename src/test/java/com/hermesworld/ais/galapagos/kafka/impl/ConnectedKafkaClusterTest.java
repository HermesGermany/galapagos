package com.hermesworld.ais.galapagos.kafka.impl;

import com.hermesworld.ais.galapagos.kafka.KafkaExecutorFactory;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.acl.*;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ConnectedKafkaClusterTest {

    @Test
    void testUpdateAcls() throws Exception {
        List<AclBindingFilter> deletedAcls = new ArrayList<>();
        List<AclBinding> createdAcls = new ArrayList<>();

        AdminClientStub adminClient = new AdminClientStub() {
            @Override
            public KafkaFuture<Void> createAcls(Collection<AclBinding> acls) {
                createdAcls.addAll(acls);
                return super.createAcls(acls);
            }

            @Override
            public KafkaFuture<Collection<AclBinding>> deleteAcls(Collection<AclBindingFilter> filters) {
                deletedAcls.addAll(filters);
                return super.deleteAcls(filters);
            }
        };

        AclBinding toRemove = new AclBinding(new ResourcePattern(ResourceType.TOPIC, "topic1", PatternType.LITERAL),
                new AccessControlEntry("User:CN=testuser", "*", AclOperation.ALL, AclPermissionType.ALLOW));
        AclBinding toKeep = new AclBinding(new ResourcePattern(ResourceType.TOPIC, "topic2", PatternType.LITERAL),
                new AccessControlEntry("User:CN=testuser", "*", AclOperation.ALL, AclPermissionType.ALLOW));
        AclBinding toCreate = new AclBinding(new ResourcePattern(ResourceType.TOPIC, "topic3", PatternType.LITERAL),
                new AccessControlEntry("User:CN=testuser", "*", AclOperation.ALL, AclPermissionType.ALLOW));

        adminClient.getAclBindings().add(toRemove);
        adminClient.getAclBindings().add(toKeep);

        KafkaExecutorFactory executorFactory = () -> Executors.newSingleThreadExecutor();
        KafkaFutureDecoupler futureDecoupler = new KafkaFutureDecoupler(executorFactory);

        @SuppressWarnings("unchecked")
        ConnectedKafkaCluster cluster = new ConnectedKafkaCluster("_test", mock(KafkaRepositoryContainer.class),
                adminClient, mock(KafkaConsumerFactory.class), futureDecoupler);

        cluster.updateUserAcls(new KafkaUser() {

            @Override
            public Collection<AclBinding> getRequiredAclBindings() {
                return Set.of(toKeep, toCreate);
            }

            @Override
            public String getKafkaUserName() {
                return "User:CN=testuser";
            }
        }).get();

        assertEquals(1, deletedAcls.size());
        assertTrue(deletedAcls.get(0).matches(toRemove));
        assertFalse(deletedAcls.get(0).matches(toKeep));
        assertEquals(1, createdAcls.size());
        assertTrue(createdAcls.contains(toCreate));
    }

}
