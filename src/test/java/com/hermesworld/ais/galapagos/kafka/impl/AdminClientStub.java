package com.hermesworld.ais.galapagos.kafka.impl;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.*;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.apache.kafka.common.quota.ClientQuotaAlteration;
import org.apache.kafka.common.quota.ClientQuotaFilter;
import org.mockito.Mockito;
import org.springframework.kafka.KafkaException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.mockito.Mockito.when;

@SuppressWarnings("deprecation")
public class AdminClientStub extends AdminClient {

    private final List<AclBinding> aclBindings = new ArrayList<>();

    private final List<NewTopic> topics = new ArrayList<>();

    private ThreadFactory kafkaThreadFactory;

    private boolean failOnDescribeCluster;

    public List<AclBinding> getAclBindings() {
        return aclBindings;
    }

    public List<NewTopic> getTopics() {
        return topics;
    }

    public void setKafkaThreadFactory(ThreadFactory kafkaThreadFactory) {
        this.kafkaThreadFactory = kafkaThreadFactory;
    }

    public void setFailOnDescribeCluster(boolean failOnDescribeCluster) {
        this.failOnDescribeCluster = failOnDescribeCluster;
    }

    @Override
    public void close(long duration, TimeUnit unit) {
    }

    @Override
    public void close(Duration timeout) {
    }

    @Override
    public CreateTopicsResult createTopics(Collection<NewTopic> newTopics, CreateTopicsOptions options) {
        topics.addAll(newTopics);
        CreateTopicsResult result = Mockito.mock(CreateTopicsResult.class);
        Mockito.when(result.all()).thenReturn(completedFuture(null));
        return result;
    }

    @Override
    public DeleteTopicsResult deleteTopics(Collection<String> topics, DeleteTopicsOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public ListTopicsResult listTopics(ListTopicsOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DescribeTopicsResult describeTopics(Collection<String> topicNames, DescribeTopicsOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DescribeClusterResult describeCluster(DescribeClusterOptions options) {
        DescribeClusterResult result = Mockito.mock(DescribeClusterResult.class);
        Node node = new Node(1, "localhost", 1);
        if (failOnDescribeCluster) {
            when(result.nodes()).thenReturn(failingFuture(new KafkaException("Kafka failed")));
        }
        else {
            when(result.nodes()).thenReturn(completedFuture(List.of(node)));
        }
        return result;
    }

    @Override
    public DescribeAclsResult describeAcls(AclBindingFilter filter, DescribeAclsOptions options) {
        List<AclBinding> matches = this.aclBindings.stream().filter(filter::matches).collect(Collectors.toList());
        DescribeAclsResult result = Mockito.mock(DescribeAclsResult.class);
        Mockito.when(result.values()).thenReturn(completedFuture(matches));
        return result;
    }

    @Override
    public CreateAclsResult createAcls(Collection<AclBinding> acls, CreateAclsOptions options) {
        this.aclBindings.addAll(acls);
        CreateAclsResult result = Mockito.mock(CreateAclsResult.class);
        Mockito.when(result.all()).thenReturn(completedFuture(null));
        return result;
    }

    @Override
    public DeleteAclsResult deleteAcls(Collection<AclBindingFilter> filters, DeleteAclsOptions options) {
        Set<AclBinding> removes = new HashSet<>();
        filters.forEach(filter -> this.aclBindings.stream().filter(filter::matches).forEach(removes::add));
        this.aclBindings.removeAll(removes);
        DeleteAclsResult result = Mockito.mock(DeleteAclsResult.class);
        Mockito.when(result.all()).thenReturn(completedFuture(null));
        return result;
    }

    @Override
    public DescribeConfigsResult describeConfigs(Collection<ConfigResource> resources, DescribeConfigsOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public AlterConfigsResult alterConfigs(Map<ConfigResource, Config> configs, AlterConfigsOptions options) {
        // currently not implemented in stub
        AlterConfigsResult result = Mockito.mock(AlterConfigsResult.class);
        Mockito.when(result.all()).thenReturn(completedFuture(null));
        return result;
    }

    @Override
    public AlterConfigsResult incrementalAlterConfigs(Map<ConfigResource, Collection<AlterConfigOp>> map,
            AlterConfigsOptions alterConfigsOptions) {
        return null;
    }

    @Override
    public AlterReplicaLogDirsResult alterReplicaLogDirs(Map<TopicPartitionReplica, String> replicaAssignment,
            AlterReplicaLogDirsOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DescribeLogDirsResult describeLogDirs(Collection<Integer> brokers, DescribeLogDirsOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DescribeReplicaLogDirsResult describeReplicaLogDirs(Collection<TopicPartitionReplica> replicas,
            DescribeReplicaLogDirsOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public CreatePartitionsResult createPartitions(Map<String, NewPartitions> newPartitions,
            CreatePartitionsOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DeleteRecordsResult deleteRecords(Map<TopicPartition, RecordsToDelete> recordsToDelete,
            DeleteRecordsOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public CreateDelegationTokenResult createDelegationToken(CreateDelegationTokenOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public RenewDelegationTokenResult renewDelegationToken(byte[] hmac, RenewDelegationTokenOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public ExpireDelegationTokenResult expireDelegationToken(byte[] hmac, ExpireDelegationTokenOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DescribeDelegationTokenResult describeDelegationToken(DescribeDelegationTokenOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DescribeConsumerGroupsResult describeConsumerGroups(Collection<String> groupIds,
            DescribeConsumerGroupsOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public ListConsumerGroupsResult listConsumerGroups(ListConsumerGroupsOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public ListConsumerGroupOffsetsResult listConsumerGroupOffsets(String groupId,
            ListConsumerGroupOffsetsOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DeleteConsumerGroupsResult deleteConsumerGroups(Collection<String> groupIds,
            DeleteConsumerGroupsOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DeleteConsumerGroupOffsetsResult deleteConsumerGroupOffsets(String s, Set<TopicPartition> set,
            DeleteConsumerGroupOffsetsOptions deleteConsumerGroupOffsetsOptions) {
        return null;
    }

    @Override
    public ElectPreferredLeadersResult electPreferredLeaders(Collection<TopicPartition> partitions,
            ElectPreferredLeadersOptions options) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public ElectLeadersResult electLeaders(ElectionType electionType, Set<TopicPartition> set,
            ElectLeadersOptions electLeadersOptions) {
        return null;
    }

    @Override
    public AlterPartitionReassignmentsResult alterPartitionReassignments(
            Map<TopicPartition, Optional<NewPartitionReassignment>> map,
            AlterPartitionReassignmentsOptions alterPartitionReassignmentsOptions) {
        return null;
    }

    @Override
    public ListPartitionReassignmentsResult listPartitionReassignments(Optional<Set<TopicPartition>> optional,
            ListPartitionReassignmentsOptions listPartitionReassignmentsOptions) {
        return null;
    }

    @Override
    public RemoveMembersFromConsumerGroupResult removeMembersFromConsumerGroup(String s,
            RemoveMembersFromConsumerGroupOptions removeMembersFromConsumerGroupOptions) {
        return null;
    }

    @Override
    public AlterConsumerGroupOffsetsResult alterConsumerGroupOffsets(String s,
            Map<TopicPartition, OffsetAndMetadata> map,
            AlterConsumerGroupOffsetsOptions alterConsumerGroupOffsetsOptions) {
        return null;
    }

    @Override
    public ListOffsetsResult listOffsets(Map<TopicPartition, OffsetSpec> map, ListOffsetsOptions listOffsetsOptions) {
        return null;
    }

    @Override
    public Map<MetricName, ? extends Metric> metrics() {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DescribeClientQuotasResult describeClientQuotas(ClientQuotaFilter filter) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DescribeClientQuotasResult describeClientQuotas(ClientQuotaFilter clientQuotaFilter,
            DescribeClientQuotasOptions describeClientQuotasOptions) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public AlterClientQuotasResult alterClientQuotas(Collection<ClientQuotaAlteration> entries) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public AlterClientQuotasResult alterClientQuotas(Collection<ClientQuotaAlteration> collection,
            AlterClientQuotasOptions alterClientQuotasOptions) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DescribeUserScramCredentialsResult describeUserScramCredentials() {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DescribeUserScramCredentialsResult describeUserScramCredentials(List<String> users) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DescribeUserScramCredentialsResult describeUserScramCredentials(List<String> list,
            DescribeUserScramCredentialsOptions describeUserScramCredentialsOptions) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public AlterUserScramCredentialsResult alterUserScramCredentials(List<UserScramCredentialAlteration> alterations) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public AlterUserScramCredentialsResult alterUserScramCredentials(List<UserScramCredentialAlteration> list,
            AlterUserScramCredentialsOptions alterUserScramCredentialsOptions) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DescribeFeaturesResult describeFeatures() {
        // Auto-generated method stub
        return null;
    }

    @Override
    public DescribeFeaturesResult describeFeatures(DescribeFeaturesOptions describeFeaturesOptions) {
        // Auto-generated method stub
        return null;
    }

    @Override
    public UpdateFeaturesResult updateFeatures(Map<String, FeatureUpdate> map,
            UpdateFeaturesOptions updateFeaturesOptions) {
        // Auto-generated method stub
        return null;
    }

    private <T> KafkaFuture<T> completedFuture(T value) {
        if (kafkaThreadFactory != null) {
            KafkaFutureImpl<T> result = new KafkaFutureImpl<>();
            Runnable r = () -> {
                // to force that callers receive a non-completed future, we have to spend some time here
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException e) {
                    return;
                }
                result.complete(value);
            };
            Thread t = kafkaThreadFactory.newThread(r);
            t.start();
            return result;
        }
        return KafkaFuture.completedFuture(value);
    }

    private <T> KafkaFuture<T> failingFuture(Throwable ex) {
        KafkaFutureImpl<T> result = new KafkaFutureImpl<>();
        Runnable r = () -> {
            // to force that callers receive a non-completed future, we have to spend some time here
            try {
                Thread.sleep(100);
            }
            catch (InterruptedException e) {
                return;
            }
            result.completeExceptionally(ex);
        };

        Thread t = kafkaThreadFactory != null ? kafkaThreadFactory.newThread(r) : new Thread(r);
        t.start();

        return result;
    }
}
