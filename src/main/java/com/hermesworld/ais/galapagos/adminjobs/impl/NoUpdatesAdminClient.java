package com.hermesworld.ais.galapagos.adminjobs.impl;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.*;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.quota.ClientQuotaAlteration;
import org.apache.kafka.common.quota.ClientQuotaFilter;

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
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateTopicsResult createTopics(Collection<NewTopic> newTopics, CreateTopicsOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteTopicsResult deleteTopics(Collection<String> topics) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteTopicsResult deleteTopics(Collection<String> topics, DeleteTopicsOptions options) {
        throw new UnsupportedOperationException();
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
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateAclsResult createAcls(Collection<AclBinding> acls, CreateAclsOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteAclsResult deleteAcls(Collection<AclBindingFilter> filters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteAclsResult deleteAcls(Collection<AclBindingFilter> filters, DeleteAclsOptions options) {
        throw new UnsupportedOperationException();
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
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public AlterConfigsResult alterConfigs(Map<ConfigResource, Config> configs, AlterConfigsOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AlterConfigsResult incrementalAlterConfigs(Map<ConfigResource, Collection<AlterConfigOp>> configs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AlterConfigsResult incrementalAlterConfigs(Map<ConfigResource, Collection<AlterConfigOp>> configs,
            AlterConfigsOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AlterReplicaLogDirsResult alterReplicaLogDirs(Map<TopicPartitionReplica, String> replicaAssignment) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AlterReplicaLogDirsResult alterReplicaLogDirs(Map<TopicPartitionReplica, String> replicaAssignment,
            AlterReplicaLogDirsOptions options) {
        throw new UnsupportedOperationException();
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
        throw new UnsupportedOperationException();
    }

    @Override
    public CreatePartitionsResult createPartitions(Map<String, NewPartitions> newPartitions,
            CreatePartitionsOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteRecordsResult deleteRecords(Map<TopicPartition, RecordsToDelete> recordsToDelete) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteRecordsResult deleteRecords(Map<TopicPartition, RecordsToDelete> recordsToDelete,
            DeleteRecordsOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateDelegationTokenResult createDelegationToken() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateDelegationTokenResult createDelegationToken(CreateDelegationTokenOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RenewDelegationTokenResult renewDelegationToken(byte[] hmac) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RenewDelegationTokenResult renewDelegationToken(byte[] hmac, RenewDelegationTokenOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ExpireDelegationTokenResult expireDelegationToken(byte[] hmac) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ExpireDelegationTokenResult expireDelegationToken(byte[] hmac, ExpireDelegationTokenOptions options) {
        throw new UnsupportedOperationException();
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
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteConsumerGroupsResult deleteConsumerGroups(Collection<String> groupIds) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteConsumerGroupOffsetsResult deleteConsumerGroupOffsets(String groupId, Set<TopicPartition> partitions,
            DeleteConsumerGroupOffsetsOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteConsumerGroupOffsetsResult deleteConsumerGroupOffsets(String groupId, Set<TopicPartition> partitions) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public ElectPreferredLeadersResult electPreferredLeaders(Collection<TopicPartition> partitions) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public ElectPreferredLeadersResult electPreferredLeaders(Collection<TopicPartition> partitions,
            ElectPreferredLeadersOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ElectLeadersResult electLeaders(ElectionType electionType, Set<TopicPartition> partitions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ElectLeadersResult electLeaders(ElectionType electionType, Set<TopicPartition> partitions,
            ElectLeadersOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AlterPartitionReassignmentsResult alterPartitionReassignments(
            Map<TopicPartition, Optional<NewPartitionReassignment>> reassignments) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AlterPartitionReassignmentsResult alterPartitionReassignments(
            Map<TopicPartition, Optional<NewPartitionReassignment>> reassignments,
            AlterPartitionReassignmentsOptions options) {
        throw new UnsupportedOperationException();
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
        throw new UnsupportedOperationException();
    }

    @Override
    public AlterConsumerGroupOffsetsResult alterConsumerGroupOffsets(String groupId,
            Map<TopicPartition, OffsetAndMetadata> offsets) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AlterConsumerGroupOffsetsResult alterConsumerGroupOffsets(String groupId,
            Map<TopicPartition, OffsetAndMetadata> offsets, AlterConsumerGroupOffsetsOptions options) {
        throw new UnsupportedOperationException();
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
    public DescribeClientQuotasResult describeClientQuotas(ClientQuotaFilter filter) {
        return delegate.describeClientQuotas(filter);
    }

    @Override
    public DescribeClientQuotasResult describeClientQuotas(ClientQuotaFilter clientQuotaFilter,
            DescribeClientQuotasOptions describeClientQuotasOptions) {
        return delegate.describeClientQuotas(clientQuotaFilter, describeClientQuotasOptions);
    }

    @Override
    public AlterClientQuotasResult alterClientQuotas(Collection<ClientQuotaAlteration> entries) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AlterClientQuotasResult alterClientQuotas(Collection<ClientQuotaAlteration> collection,
            AlterClientQuotasOptions alterClientQuotasOptions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DescribeUserScramCredentialsResult describeUserScramCredentials() {
        return delegate.describeUserScramCredentials();
    }

    @Override
    public DescribeUserScramCredentialsResult describeUserScramCredentials(List<String> users) {
        return delegate.describeUserScramCredentials(users);
    }

    @Override
    public DescribeUserScramCredentialsResult describeUserScramCredentials(List<String> list,
            DescribeUserScramCredentialsOptions describeUserScramCredentialsOptions) {
        return delegate.describeUserScramCredentials(list, describeUserScramCredentialsOptions);
    }

    @Override
    public AlterUserScramCredentialsResult alterUserScramCredentials(List<UserScramCredentialAlteration> alterations) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AlterUserScramCredentialsResult alterUserScramCredentials(List<UserScramCredentialAlteration> list,
            AlterUserScramCredentialsOptions alterUserScramCredentialsOptions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DescribeFeaturesResult describeFeatures() {
        return delegate.describeFeatures();
    }

    @Override
    public DescribeFeaturesResult describeFeatures(DescribeFeaturesOptions describeFeaturesOptions) {
        return delegate.describeFeatures(describeFeaturesOptions);
    }

    @Override
    public UpdateFeaturesResult updateFeatures(Map<String, FeatureUpdate> map,
            UpdateFeaturesOptions updateFeaturesOptions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<MetricName, ? extends Metric> metrics() {
        return delegate.metrics();
    }
}
