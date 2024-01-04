package com.hermesworld.ais.galapagos.kafka.impl;

import com.hermesworld.ais.galapagos.kafka.KafkaClusterAdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.springframework.kafka.KafkaException;

import java.util.*;
import java.util.concurrent.ThreadFactory;

public class AdminClientStub implements KafkaClusterAdminClient {

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
    public KafkaFuture<Void> createTopic(NewTopic topic) {
        topics.add(topic);
        return completedFuture(null);
    }

    @Override
    public KafkaFuture<Void> deleteTopic(String topicName) {
        topics.removeIf(t -> topicName.equals(t.name()));
        return completedFuture(null);
    }

    @Override
    public KafkaFuture<TopicDescription> describeTopic(String topicName) {
        if (topics.stream().anyMatch(t -> topicName.equals(t.name()))) {
            return completedFuture(new TopicDescription(topicName, false, List.of()));
        }

        return completedFuture(null);
    }

    @Override
    public KafkaFuture<Collection<Node>> describeCluster() {
        Node node = new Node(1, "localhost", 1);
        if (failOnDescribeCluster) {
            return failingFuture(new KafkaException("Kafka failed"));
        }
        return completedFuture(List.of(node));
    }

    @Override
    public KafkaFuture<Collection<AclBinding>> describeAcls(AclBindingFilter filter) {
        List<AclBinding> matches = this.aclBindings.stream().filter(filter::matches).toList();
        return completedFuture(matches);
    }

    @Override
    public KafkaFuture<Void> createAcls(Collection<AclBinding> bindings) {
        this.aclBindings.addAll(bindings);
        return completedFuture(null);
    }

    @Override
    public KafkaFuture<Collection<AclBinding>> deleteAcls(Collection<AclBindingFilter> filters) {
        Set<AclBinding> removes = new HashSet<>();
        filters.forEach(filter -> this.aclBindings.stream().filter(filter::matches).forEach(removes::add));
        this.aclBindings.removeAll(removes);
        return completedFuture(removes);
    }

    @Override
    public KafkaFuture<Config> describeConfigs(ConfigResource resource) {
        return completedFuture(null);
    }

    @Override
    public KafkaFuture<Void> incrementalAlterConfigs(ConfigResource resource, Map<String, String> configValues) {
        return completedFuture(null);
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
