package com.hermesworld.ais.galapagos.adminjobs.impl;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.*;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.config.ConfigResource;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Helper class used by admin jobs providing a "dry run". This class wraps an existing Kafka AdminClient and throws an
 * <code>UnsupportedOperationException</code> whenever a method is called which would modify something in the Kafka
 * cluster. Admin jobs subclass this class and override the calls they are interested in.
 */
@SuppressWarnings("deprecation")
public abstract class NoUpdatesAdminClient extends AdminClient {

    private final AdminClient delegate;

    public NoUpdatesAdminClient(AdminClient delegate) {
        this.delegate = delegate;

    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    @Deprecated
    public void close(long duration, TimeUnit unit) {
        delegate.close(duration, unit);
    }

    @Override
    public void close(Duration timeout) {
        delegate.close(timeout);
    }

    @Override
    public CreateTopicsResult createTopics(Collection<NewTopic> newTopics) {
        return delegate.createTopics(Collections.singletonList(new NewTopic("test", Map.of())),
                new CreateTopicsOptions().validateOnly(true));
    }

    @Override
    public CreateTopicsResult createTopics(Collection<NewTopic> newTopics, CreateTopicsOptions options) {
        return delegate.createTopics(Collections.singletonList(new NewTopic("test", Map.of())),
                new CreateTopicsOptions().validateOnly(true));
    }

    @Override
    public DeleteTopicsResult deleteTopics(Collection<String> topics) {
        return delegate.deleteTopics(List.of());
    }

    @Override
    public DeleteTopicsResult deleteTopics(Collection<String> topics, DeleteTopicsOptions options) {
        return delegate.deleteTopics(List.of());
    }

    @Override
    public ListTopicsResult listTopics() {
        return delegate.listTopics();
    }

    @Override
    public ListTopicsResult listTopics(ListTopicsOptions options) {
        return delegate.listTopics(options);
    }

    @Override
    public DescribeTopicsResult describeTopics(Collection<String> topicNames) {
        return delegate.describeTopics(topicNames);
    }

    @Override
    public DescribeTopicsResult describeTopics(Collection<String> topicNames, DescribeTopicsOptions options) {
        return delegate.describeTopics(topicNames, options);
    }

    @Override
    public DescribeClusterResult describeCluster() {
        return delegate.describeCluster();
    }

    @Override
    public DescribeClusterResult describeCluster(DescribeClusterOptions options) {
        return delegate.describeCluster(options);
    }

    @Override
    public DescribeAclsResult describeAcls(AclBindingFilter filter) {
        return delegate.describeAcls(filter);
    }

    @Override
    public DescribeAclsResult describeAcls(AclBindingFilter filter, DescribeAclsOptions options) {
        return delegate.describeAcls(filter, options);
    }

    @Override
    public CreateAclsResult createAcls(Collection<AclBinding> acls) {
        return delegate.createAcls(List.of());
    }

    @Override
    public CreateAclsResult createAcls(Collection<AclBinding> acls, CreateAclsOptions options) {
        return delegate.createAcls(List.of());
    }

    @Override
    public DeleteAclsResult deleteAcls(Collection<AclBindingFilter> filters) {
        return delegate.deleteAcls(List.of());
    }

    @Override
    public DeleteAclsResult deleteAcls(Collection<AclBindingFilter> filters, DeleteAclsOptions options) {
        return delegate.deleteAcls(List.of());
    }

    @Override
    public DescribeConfigsResult describeConfigs(Collection<ConfigResource> resources) {
        return delegate.describeConfigs(resources);
    }

    @Override
    public DescribeConfigsResult describeConfigs(Collection<ConfigResource> resources, DescribeConfigsOptions options) {
        return delegate.describeConfigs(resources, options);
    }

    @Override
    @Deprecated
    public AlterConfigsResult alterConfigs(Map<ConfigResource, Config> configs) {
        return delegate.alterConfigs(Map.of(), new AlterConfigsOptions().validateOnly(true));
    }

    @Override
    @Deprecated
    public AlterConfigsResult alterConfigs(Map<ConfigResource, Config> configs, AlterConfigsOptions options) {
        return delegate.alterConfigs(Map.of(), new AlterConfigsOptions().validateOnly(true));
    }

    @Override
    public AlterConfigsResult incrementalAlterConfigs(Map<ConfigResource, Collection<AlterConfigOp>> configs) {
        return delegate.incrementalAlterConfigs(Map.of(), new AlterConfigsOptions().validateOnly(true));
    }

    @Override
    public AlterConfigsResult incrementalAlterConfigs(Map<ConfigResource, Collection<AlterConfigOp>> configs,
            AlterConfigsOptions options) {
        return delegate.incrementalAlterConfigs(Map.of(), new AlterConfigsOptions().validateOnly(true));
    }

    @Override
    public AlterReplicaLogDirsResult alterReplicaLogDirs(Map<TopicPartitionReplica, String> replicaAssignment) {
        return delegate.alterReplicaLogDirs(Map.of());
    }

    @Override
    public AlterReplicaLogDirsResult alterReplicaLogDirs(Map<TopicPartitionReplica, String> replicaAssignment,
            AlterReplicaLogDirsOptions options) {
        return delegate.alterReplicaLogDirs(Map.of());
    }

    @Override
    public DescribeLogDirsResult describeLogDirs(Collection<Integer> brokers) {
        return delegate.describeLogDirs(brokers);
    }

    @Override
    public DescribeLogDirsResult describeLogDirs(Collection<Integer> brokers, DescribeLogDirsOptions options) {
        return delegate.describeLogDirs(brokers, options);
    }

    @Override
    public DescribeReplicaLogDirsResult describeReplicaLogDirs(Collection<TopicPartitionReplica> replicas) {
        return delegate.describeReplicaLogDirs(replicas);
    }

    @Override
    public DescribeReplicaLogDirsResult describeReplicaLogDirs(Collection<TopicPartitionReplica> replicas,
            DescribeReplicaLogDirsOptions options) {
        return delegate.describeReplicaLogDirs(replicas, options);
    }

    @Override
    public CreatePartitionsResult createPartitions(Map<String, NewPartitions> newPartitions) {
        return delegate.createPartitions(Map.of(), new CreatePartitionsOptions().validateOnly(true));
    }

    @Override
    public CreatePartitionsResult createPartitions(Map<String, NewPartitions> newPartitions,
            CreatePartitionsOptions options) {
        return delegate.createPartitions(Map.of(), new CreatePartitionsOptions().validateOnly(true));
    }

    @Override
    public DeleteRecordsResult deleteRecords(Map<TopicPartition, RecordsToDelete> recordsToDelete) {
        return delegate.deleteRecords(Map.of());
    }

    @Override
    public DeleteRecordsResult deleteRecords(Map<TopicPartition, RecordsToDelete> recordsToDelete,
            DeleteRecordsOptions options) {
        return delegate.deleteRecords(Map.of());
    }

    @Override
    public CreateDelegationTokenResult createDelegationToken() {
        return delegate.createDelegationToken(new CreateDelegationTokenOptions());
    }

    @Override
    public CreateDelegationTokenResult createDelegationToken(CreateDelegationTokenOptions options) {
        return delegate.createDelegationToken(new CreateDelegationTokenOptions());
    }

    @Override
    public RenewDelegationTokenResult renewDelegationToken(byte[] hmac) {
        return delegate.renewDelegationToken(new byte[] {});
    }

    @Override
    public RenewDelegationTokenResult renewDelegationToken(byte[] hmac, RenewDelegationTokenOptions options) {
        return delegate.renewDelegationToken(new byte[] {});
    }

    @Override
    public ExpireDelegationTokenResult expireDelegationToken(byte[] hmac) {
        return delegate.expireDelegationToken(new byte[] {});
    }

    @Override
    public ExpireDelegationTokenResult expireDelegationToken(byte[] hmac, ExpireDelegationTokenOptions options) {
        return delegate.expireDelegationToken(new byte[] {});
    }

    @Override
    public DescribeDelegationTokenResult describeDelegationToken() {
        return delegate.describeDelegationToken();
    }

    @Override
    public DescribeDelegationTokenResult describeDelegationToken(DescribeDelegationTokenOptions options) {
        return delegate.describeDelegationToken(options);
    }

    @Override
    public DescribeConsumerGroupsResult describeConsumerGroups(Collection<String> groupIds,
            DescribeConsumerGroupsOptions options) {
        return delegate.describeConsumerGroups(groupIds, options);
    }

    @Override
    public DescribeConsumerGroupsResult describeConsumerGroups(Collection<String> groupIds) {
        return delegate.describeConsumerGroups(groupIds);
    }

    @Override
    public ListConsumerGroupsResult listConsumerGroups(ListConsumerGroupsOptions options) {
        return delegate.listConsumerGroups(options);
    }

    @Override
    public ListConsumerGroupsResult listConsumerGroups() {
        return delegate.listConsumerGroups();
    }

    @Override
    public ListConsumerGroupOffsetsResult listConsumerGroupOffsets(String groupId,
            ListConsumerGroupOffsetsOptions options) {
        return delegate.listConsumerGroupOffsets(groupId, options);
    }

    @Override
    public ListConsumerGroupOffsetsResult listConsumerGroupOffsets(String groupId) {
        return delegate.listConsumerGroupOffsets(groupId);
    }

    @Override
    public DeleteConsumerGroupsResult deleteConsumerGroups(Collection<String> groupIds,
            DeleteConsumerGroupsOptions options) {
        return delegate.deleteConsumerGroups(List.of());
    }

    @Override
    public DeleteConsumerGroupsResult deleteConsumerGroups(Collection<String> groupIds) {
        return delegate.deleteConsumerGroups(List.of());
    }

    @Override
    public DeleteConsumerGroupOffsetsResult deleteConsumerGroupOffsets(String groupId, Set<TopicPartition> partitions,
            DeleteConsumerGroupOffsetsOptions options) {
        return delegate.deleteConsumerGroupOffsets("", Set.of());
    }

    @Override
    public DeleteConsumerGroupOffsetsResult deleteConsumerGroupOffsets(String groupId, Set<TopicPartition> partitions) {
        return delegate.deleteConsumerGroupOffsets("", Set.of());
    }

    @Override
    @Deprecated
    public ElectPreferredLeadersResult electPreferredLeaders(Collection<TopicPartition> partitions) {
        return delegate.electPreferredLeaders(List.of());
    }

    @Override
    @Deprecated
    public ElectPreferredLeadersResult electPreferredLeaders(Collection<TopicPartition> partitions,
            ElectPreferredLeadersOptions options) {
        return delegate.electPreferredLeaders(List.of());
    }

    @Override
    public ElectLeadersResult electLeaders(ElectionType electionType, Set<TopicPartition> partitions) {
        return delegate.electLeaders(electionType, Set.of());
    }

    @Override
    public ElectLeadersResult electLeaders(ElectionType electionType, Set<TopicPartition> partitions,
            ElectLeadersOptions options) {
        return delegate.electLeaders(electionType, Set.of());
    }

    @Override
    public AlterPartitionReassignmentsResult alterPartitionReassignments(
            Map<TopicPartition, Optional<NewPartitionReassignment>> reassignments) {
        return delegate.alterPartitionReassignments(Map.of());
    }

    @Override
    public AlterPartitionReassignmentsResult alterPartitionReassignments(
            Map<TopicPartition, Optional<NewPartitionReassignment>> reassignments,
            AlterPartitionReassignmentsOptions options) {
        return delegate.alterPartitionReassignments(Map.of());
    }

    @Override
    public ListPartitionReassignmentsResult listPartitionReassignments() {
        return delegate.listPartitionReassignments();
    }

    @Override
    public ListPartitionReassignmentsResult listPartitionReassignments(Set<TopicPartition> partitions) {
        return delegate.listPartitionReassignments(partitions);
    }

    @Override
    public ListPartitionReassignmentsResult listPartitionReassignments(Set<TopicPartition> partitions,
            ListPartitionReassignmentsOptions options) {
        return delegate.listPartitionReassignments(partitions, options);
    }

    @Override
    public ListPartitionReassignmentsResult listPartitionReassignments(ListPartitionReassignmentsOptions options) {
        return delegate.listPartitionReassignments(options);
    }

    @Override
    public ListPartitionReassignmentsResult listPartitionReassignments(Optional<Set<TopicPartition>> partitions,
            ListPartitionReassignmentsOptions options) {
        return delegate.listPartitionReassignments(partitions, options);
    }

    @Override
    public RemoveMembersFromConsumerGroupResult removeMembersFromConsumerGroup(String groupId,
            RemoveMembersFromConsumerGroupOptions options) {
        return delegate.removeMembersFromConsumerGroup("testgroup", options);
    }

    @Override
    public AlterConsumerGroupOffsetsResult alterConsumerGroupOffsets(String groupId,
            Map<TopicPartition, OffsetAndMetadata> offsets) {
        return delegate.alterConsumerGroupOffsets("testgroup", Map.of());
    }

    @Override
    public AlterConsumerGroupOffsetsResult alterConsumerGroupOffsets(String groupId,
            Map<TopicPartition, OffsetAndMetadata> offsets, AlterConsumerGroupOffsetsOptions options) {
        return delegate.alterConsumerGroupOffsets("testgroup", Map.of());
    }

    @Override
    public ListOffsetsResult listOffsets(Map<TopicPartition, OffsetSpec> topicPartitionOffsets) {
        return delegate.listOffsets(topicPartitionOffsets);
    }

    @Override
    public ListOffsetsResult listOffsets(Map<TopicPartition, OffsetSpec> topicPartitionOffsets,
            ListOffsetsOptions options) {
        return delegate.listOffsets(topicPartitionOffsets, options);
    }

    @Override
    public Map<MetricName, ? extends Metric> metrics() {
        return delegate.metrics();
    }
}
