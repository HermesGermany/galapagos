package com.hermesworld.ais.galapagos.topics.service;

import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.topics.SchemaCompatCheckMode;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import javax.annotation.CheckReturnValue;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface TopicService {

    @CheckReturnValue
    CompletableFuture<TopicMetadata> createTopic(String environmentId, TopicMetadata topic, Integer partitionCount,
                                                 Map<String, String> topicConfig);

    @CheckReturnValue
    boolean canDeleteTopic(String environmentId, String topicName);

    @CheckReturnValue
    CompletableFuture<Void> deleteTopic(String environmentId, String topicName);

    @CheckReturnValue
    CompletableFuture<Void> updateTopicDescription(String environmentId, String topicName, String newDescription);

    /**
     * Marks the given topic as deprecated. Unlike most other operations, this is done automatically on <b>all</b> known
     * clusters (environments) where the topic exists.
     *
     * @param topicName       Name of the topic.
     * @param deprecationText Deprecation text, e.g. a reasoning and a reference to an alternative topic.
     * @param eolDate         EOL date until when the topic is guaranteed to exist. The topic can only be deleted before
     *                        this date when it has no more subscribers (standard topic deletion rules and order apply).
     * @return A future which completed once the topic has been deprecated on all known clusters, or which completes
     * exceptionally when the deprecation could not be performed successfully. Note that the topic may be
     * deprecated on <b>some</b> stages even if the future completes exceptionally.
     */
    @CheckReturnValue
    CompletableFuture<Void> markTopicDeprecated(String topicName, String deprecationText, LocalDate eolDate);

    /**
     * Removes the deprecation mark from the given topic. Unlike most other operations, this is done automatically on
     * <b>all</b> known clusters (environments) where the topic exists.
     *
     * @param topicName Name of the topic.
     * @return A future which completed once the topic has been undeprecated on all known clusters, or which completes
     * exceptionally when the deprecation removal could not be performed successfully. Note that the topic may
     * be deprecated on <b>some</b> stages even if the future completes exceptionally.
     */
    @CheckReturnValue
    CompletableFuture<Void> unmarkTopicDeprecated(String topicName);

    /**
     * Updates the "subscriptionApprovalRequired" flag for a given topic on a given environment. This is a stageable
     * change, and can only be performed on non-"staging only" environments. <br>
     * If the flag is activated for a topic, all already existing subscriptions on that environment for the topic will
     * initially receive the <code>APPROVED</code> state. This can be updated using the
     * <code>SubscriptionService</code>. <br>
     * Once it is deactivated for a topic, all <code>PENDING</code> subscriptions for the topic on the given environment
     * will be converted to <code>APPROVED</code> ones (this is not done by the <code>TopicService</code>, but by the
     * class <code>SubscriptionTopicListener</code>).
     *
     * @param environmentId                ID of the environment containing the topic. Must not be a "staging only"
     *                                     environment.
     * @param topicName                    Name of the topic to update.
     * @param subscriptionApprovalRequired New flag for the topic.
     * @return A completable future which completes once all related changes are performed.
     */
    @CheckReturnValue
    CompletableFuture<Void> setSubscriptionApprovalRequiredFlag(String environmentId, String topicName,
                                                                boolean subscriptionApprovalRequired);

    @CheckReturnValue
    List<TopicMetadata> listTopics(String environmentId);

    @CheckReturnValue
    Optional<TopicMetadata> getTopic(String environmentId, String topicName);

    @CheckReturnValue
    List<SchemaMetadata> getTopicSchemaVersions(String environmentId, String topicName);

    @CheckReturnValue
    Optional<SchemaMetadata> getSchemaById(String environmentId, String schemaId);

    @CheckReturnValue
    CompletableFuture<SchemaMetadata> addTopicSchemaVersion(String environmentId, String topicName, String jsonSchema,
                                                            String changeDescription, SchemaCompatCheckMode skipCompatCheck);

    @CheckReturnValue
    CompletableFuture<Void> deleteLatestTopicSchemaVersion(String environmentId, String topicName);

    /**
     * Adds a new JSON schema version to this topic. This variant of this method takes a complete {@link SchemaMetadata}
     * object, which is usually used during staging only. This method checks that the preceding version of the schema
     * exists on the given environment, e.g. you can add a schema version #3 only if #2 is already existing. If this
     * condition is not fulfilled, a failed <code>CompletableFuture</code> (with an {@link IllegalStateException} as
     * cause) is returned.
     *
     * @param environmentId Environment ID to add the schema version to.
     * @param metadata      Metadata of the schema to add, including topic name and schema version.
     * @return A Future which completes when the operation completes, or a failed future when the schema could not be
     * added.
     */
    @CheckReturnValue
    CompletableFuture<SchemaMetadata> addTopicSchemaVersion(String environmentId, SchemaMetadata metadata,
                                                            SchemaCompatCheckMode skipCompatCheck);

    @CheckReturnValue
    CompletableFuture<TopicCreateParams> buildTopicCreateParams(String environmentId, String topicName);

    @CheckReturnValue
    CompletableFuture<List<ConsumerRecord<String, String>>> peekTopicData(String environmentId, String topicName,
                                                                          int limit);

    @CheckReturnValue
    CompletableFuture<Void> addTopicProducer(String environmentId, String topicName, String producerId);

    @CheckReturnValue
    CompletableFuture<Void> removeTopicProducer(String environmentId, String topicName, String producerId);

    @CheckReturnValue
    CompletableFuture<Void> changeTopicOwner(String environmentId, String topicName, String newApplicationOwnerId);

    @CheckReturnValue
    Optional<TopicMetadata> getSingleTopic(String environmentId, String topicName);

}
