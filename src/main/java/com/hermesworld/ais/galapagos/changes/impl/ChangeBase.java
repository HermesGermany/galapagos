package com.hermesworld.ais.galapagos.changes.impl;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.changes.ApplicableChange;
import com.hermesworld.ais.galapagos.changes.ApplyChangeContext;
import com.hermesworld.ais.galapagos.changes.Change;
import com.hermesworld.ais.galapagos.changes.ChangeType;
import com.hermesworld.ais.galapagos.kafka.TopicCreateParams;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public abstract class ChangeBase implements ApplicableChange {

    // The "internalTopic" flag on some change classes is used for GUI filtering purposes only!

    private final ChangeType changeType;

    ChangeBase(ChangeType changeType) {
        this.changeType = changeType;
    }

    @Override
    public ChangeType getChangeType() {
        return changeType;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }

        return isEqualTo((ChangeBase) obj);
    }

    @Override
    public int hashCode() {
        return changeType.hashCode();
    }

    /**
     * Implementation notice: It is enough to check for a "semantical" equality, e.g. compare the topic name only for a
     * <code>TOPIC_CREATED</code> change. You can also safely cast the parameter to your exact change class.
     *
     * @param other Change to compare this change to.
     *
     * @return <code>true</code> if this change is semantically equal to the given change, <code>false</code> otherwise.
     */
    protected abstract boolean isEqualTo(ChangeBase other);

    public static ChangeBase createTopic(TopicMetadata topicMetadata, TopicCreateParams createParams) {
        return new CreateTopicChange(topicMetadata, createParams);
    }

    public static ChangeBase subscribeTopic(SubscriptionMetadata subscriptionMetadata) {
        return new SubscribeToTopicChange(subscriptionMetadata);
    }

    public static ChangeBase deleteTopic(String topicName, boolean internalTopic) {
        return new DeleteTopicChange(topicName, internalTopic);
    }

    public static ChangeBase unsubscribeTopic(SubscriptionMetadata subscriptionMetadata) {
        return new UnsubscribeFromTopicChange(subscriptionMetadata);
    }

    public static ChangeBase updateSubscription(SubscriptionMetadata subscriptionMetadata) {
        return new UpdateSubscriptionChange(subscriptionMetadata);
    }

    public static ChangeBase updateTopicDescription(String topicName, String newDescription, boolean internalTopic) {
        return new UpdateTopicDescriptionChange(topicName, newDescription, internalTopic);
    }

    public static ChangeBase markTopicDeprecated(String topicName, String deprecationText, LocalDate eolDate) {
        return new DeprecateTopicChange(topicName, deprecationText, eolDate);
    }

    public static ChangeBase unmarkTopicDeprecated(String topicName) {
        return new UndeprecateTopicChange(topicName);
    }

    public static ChangeBase updateTopicSubscriptionApprovalRequiredFlag(String topicName,
            boolean subscriptionApprovalRequired) {
        return new UpdateSubscriptionApprovalRequiredFlagChange(topicName, subscriptionApprovalRequired);
    }

    public static ChangeBase publishTopicSchemaVersion(String topicName, SchemaMetadata schemaVersion) {
        return new PublishTopicSchemaVersionChange(topicName, schemaVersion);
    }

    public static ChangeBase deleteTopicSchemaVersion(String topicName) {
        return new DeleteTopicSchemaVersionChange(topicName);
    }

    public static ChangeBase addTopicProducer(String topicName, String producerApplicationId) {
        return new TopicProducerAddChange(topicName, producerApplicationId);
    }

    public static ChangeBase removeTopicProducer(String topicName, String producerApplicationId) {
        return new TopicProducerRemoveChange(topicName, producerApplicationId);
    }

    public static ChangeBase changeTopicOwner(String topicName, String producerApplicationId) {
        return new TopicOwnerChange(topicName, producerApplicationId);
    }

    /**
     * @deprecated Is no longer signalled by a "change", but as a Galapagos Event.
     */
    @Deprecated
    public static Change registerApplication(String applicationId, ApplicationMetadata metadata) {
        return new RegisterApplicationChange(applicationId, metadata);
    }

    /**
     * Creates a new "compound change" which consists of one main change and a (potentially empty) list of additional
     * changes. When the change is applied to an environment, the main change is applied first, the additional changes
     * afterwards (in the order of the list). The returned future of the <code>applyTo</code> method of this change
     * returns when <b>all</b> changes are complete, or fails if <b>any</b> change fails. Note that some changes may
     * have been applied successfully to the target environment even in case of failure of the future.
     *
     * @param mainChange        Main change of the new "compound change"
     * @param additionalChanges Additional changes of the new "compound change"
     *
     * @return A change which consists of all the given changes and behaves like described above.
     */
    public static ChangeBase compoundChange(ChangeBase mainChange, List<ChangeBase> additionalChanges) {
        return new CompoundChange(mainChange, additionalChanges);
    }

}

@JsonSerialize
final class CreateTopicChange extends ChangeBase {

    private final TopicMetadata topicMetadata;

    private final TopicCreateParamsDto createParams;

    CreateTopicChange(TopicMetadata topicMetadata, TopicCreateParams createParams) {
        super(ChangeType.TOPIC_CREATED);
        this.topicMetadata = new TopicMetadata(topicMetadata);
        this.createParams = new TopicCreateParamsDto(createParams);
    }

    public TopicMetadata getTopicMetadata() {
        return topicMetadata;
    }

    public TopicCreateParamsDto getCreateParams() {
        return createParams;
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        CreateTopicChange change = (CreateTopicChange) other;
        return Objects.equals(topicMetadata.getName(), change.topicMetadata.getName());
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        return context.getTopicService().createTopic(context.getTargetEnvironmentId(), topicMetadata,
                createParams.getNumberOfPartitions(), createParams.topicConfigsAsStringMap());
    }

}

@JsonSerialize
final class DeleteTopicChange extends ChangeBase {

    private final String topicName;

    private final boolean internalTopic;

    public DeleteTopicChange(String topicName, boolean internalTopic) {
        super(ChangeType.TOPIC_DELETED);
        this.topicName = topicName;
        this.internalTopic = internalTopic;
    }

    public String getTopicName() {
        return topicName;
    }

    public boolean isInternalTopic() {
        return internalTopic;
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        return Objects.equals(topicName, ((DeleteTopicChange) other).topicName);
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        return context.getTopicService().deleteTopic(context.getTargetEnvironmentId(), topicName);
    }

}

@JsonSerialize
final class SubscribeToTopicChange extends ChangeBase {

    private final SubscriptionMetadata subscriptionMetadata;

    SubscribeToTopicChange(SubscriptionMetadata subscriptionMetadata) {
        super(ChangeType.TOPIC_SUBSCRIBED);
        this.subscriptionMetadata = new SubscriptionMetadata(subscriptionMetadata);
    }

    public SubscriptionMetadata getSubscriptionMetadata() {
        return subscriptionMetadata;
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        SubscribeToTopicChange change = (SubscribeToTopicChange) other;
        return Objects.equals(subscriptionMetadata.getClientApplicationId(),
                change.subscriptionMetadata.getClientApplicationId())
                && Objects.equals(subscriptionMetadata.getTopicName(), change.subscriptionMetadata.getTopicName());
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        return context.getSubscriptionService().addSubscription(context.getTargetEnvironmentId(), subscriptionMetadata);
    }

}

@JsonSerialize
final class UpdateSubscriptionChange extends ChangeBase {

    UpdateSubscriptionChange(SubscriptionMetadata subscriptionMetadata) {
        super(ChangeType.TOPIC_SUBSCRIPTION_UPDATED);
        this.subscriptionMetadata = new SubscriptionMetadata(subscriptionMetadata);
    }

    private final SubscriptionMetadata subscriptionMetadata;

    public SubscriptionMetadata getSubscriptionMetadata() {
        return subscriptionMetadata;
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        throw new UnsupportedOperationException("Cannot stage subscription state updates");
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        UpdateSubscriptionChange change = (UpdateSubscriptionChange) other;
        return Objects.equals(subscriptionMetadata.getClientApplicationId(),
                change.subscriptionMetadata.getClientApplicationId())
                && Objects.equals(subscriptionMetadata.getTopicName(), change.subscriptionMetadata.getTopicName())
                && subscriptionMetadata.getState() == change.subscriptionMetadata.getState();
    }

}

@JsonSerialize
final class UnsubscribeFromTopicChange extends ChangeBase {

    private final SubscriptionMetadata subscriptionMetadata;

    UnsubscribeFromTopicChange(SubscriptionMetadata subscriptionMetadata) {
        super(ChangeType.TOPIC_UNSUBSCRIBED);
        this.subscriptionMetadata = new SubscriptionMetadata(subscriptionMetadata);
    }

    public SubscriptionMetadata getSubscriptionMetadata() {
        return subscriptionMetadata;
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        UnsubscribeFromTopicChange change = (UnsubscribeFromTopicChange) other;
        return Objects.equals(subscriptionMetadata.getClientApplicationId(),
                change.subscriptionMetadata.getClientApplicationId())
                && Objects.equals(subscriptionMetadata.getTopicName(), change.subscriptionMetadata.getTopicName());
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        String applId = subscriptionMetadata.getClientApplicationId();
        String topicName = subscriptionMetadata.getTopicName();

        SubscriptionMetadata toDelete = context.getSubscriptionService()
                .getSubscriptionsOfApplication(context.getTargetEnvironmentId(), applId, true).stream()
                .filter(m -> topicName.equals(m.getTopicName())).findAny().orElse(null);
        if (toDelete == null) {
            return CompletableFuture.failedFuture(new NoSuchElementException(
                    "No subscription of this application to topic " + topicName + " found on target environment."));
        }

        return context.getSubscriptionService().deleteSubscription(context.getTargetEnvironmentId(), toDelete.getId());
    }

}

@JsonSerialize
final class UpdateTopicDescriptionChange extends ChangeBase {

    private final String topicName;

    private final String newDescription;

    private final boolean internalTopic;

    public UpdateTopicDescriptionChange(String topicName, String newDescription, boolean internalTopic) {
        super(ChangeType.TOPIC_DESCRIPTION_CHANGED);
        this.topicName = topicName;
        this.newDescription = newDescription;
        this.internalTopic = internalTopic;
    }

    public String getTopicName() {
        return topicName;
    }

    public String getNewDescription() {
        return newDescription;
    }

    public boolean isInternalTopic() {
        return internalTopic;
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        return Objects.equals(topicName, ((UpdateTopicDescriptionChange) other).topicName);
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        return context.getTopicService().updateTopicDescription(context.getTargetEnvironmentId(), topicName,
                newDescription);
    }

}

@JsonSerialize
final class TopicProducerAddChange extends ChangeBase {

    private final String topicName;

    private final String producerApplicationId;

    public TopicProducerAddChange(String topicName, String producerApplicationId) {
        super(ChangeType.TOPIC_PRODUCER_APPLICATION_ADDED);
        this.topicName = topicName;
        this.producerApplicationId = producerApplicationId;

    }

    public String getTopicName() {
        return topicName;
    }

    public String getProducerApplicationId() {
        return producerApplicationId;
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        return Objects.equals(topicName, ((TopicProducerAddChange) other).topicName)
                && producerApplicationId.equals(((TopicProducerAddChange) other).producerApplicationId);
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        return context.getTopicService().addTopicProducer(context.getTargetEnvironmentId(), topicName,
                producerApplicationId);
    }

}

@JsonSerialize
final class TopicOwnerChange extends ChangeBase {

    private final String topicName;

    private final String previousOwnerApplicationId;

    public TopicOwnerChange(String topicName, String previousOwnerApplicationId) {
        super(ChangeType.TOPIC_OWNER_CHANGED);
        this.topicName = topicName;
        this.previousOwnerApplicationId = previousOwnerApplicationId;
    }

    public String getTopicName() {
        return topicName;
    }

    public String getPreviousOwnerApplicationId() {
        return previousOwnerApplicationId;
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        return Objects.equals(topicName, ((TopicOwnerChange) other).topicName)
                && previousOwnerApplicationId.equals(((TopicOwnerChange) other).previousOwnerApplicationId);
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        throw new UnsupportedOperationException("Topic Owner changes cannot be applied");
    }

}

@JsonSerialize
final class TopicProducerRemoveChange extends ChangeBase {

    private final String topicName;

    private final String producerApplicationId;

    public TopicProducerRemoveChange(String topicName, String producerApplicationId) {
        super(ChangeType.TOPIC_PRODUCER_APPLICATION_REMOVED);
        this.topicName = topicName;
        this.producerApplicationId = producerApplicationId;
    }

    public String getTopicName() {
        return topicName;
    }

    public String getProducerApplicationId() {
        return producerApplicationId;
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        return Objects.equals(topicName, ((TopicProducerRemoveChange) other).topicName)
                && producerApplicationId.equals(((TopicProducerRemoveChange) other).producerApplicationId);
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        return context.getTopicService().removeTopicProducer(context.getTargetEnvironmentId(), topicName,
                producerApplicationId);
    }

}

@JsonSerialize
final class DeprecateTopicChange extends ChangeBase {

    private final String topicName;

    private final String deprecationText;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final LocalDate eolDate;

    public DeprecateTopicChange(String topicName, String deprecationText, LocalDate eolDate) {
        super(ChangeType.TOPIC_DEPRECATED);
        this.topicName = topicName;
        this.deprecationText = deprecationText;
        this.eolDate = eolDate;
    }

    public String getTopicName() {
        return topicName;
    }

    public String getDeprecationText() {
        return deprecationText;
    }

    public LocalDate getEolDate() {
        return eolDate;
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        return Objects.equals(topicName, ((DeprecateTopicChange) other).topicName);
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        throw new UnsupportedOperationException("Deprecation changes cannot be applied");
    }

}

@JsonSerialize
final class UndeprecateTopicChange extends ChangeBase {

    private final String topicName;

    public UndeprecateTopicChange(String topicName) {
        super(ChangeType.TOPIC_UNDEPRECATED);
        this.topicName = topicName;
    }

    public String getTopicName() {
        return topicName;
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        return Objects.equals(topicName, ((UndeprecateTopicChange) other).topicName);
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        throw new UnsupportedOperationException("Deprecation changes cannot be applied");
    }
}

@JsonSerialize
final class UpdateSubscriptionApprovalRequiredFlagChange extends ChangeBase {

    private final String topicName;

    private final boolean subscriptionApprovalRequired;

    public UpdateSubscriptionApprovalRequiredFlagChange(String topicName, boolean subscriptionApprovalRequired) {
        super(ChangeType.TOPIC_SUBSCRIPTION_APPROVAL_REQUIRED_FLAG_UPDATED);
        this.topicName = topicName;
        this.subscriptionApprovalRequired = subscriptionApprovalRequired;
    }

    public String getTopicName() {
        return topicName;
    }

    public boolean isSubscriptionApprovalRequired() {
        return subscriptionApprovalRequired;
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        return Objects.equals(topicName, ((UpdateSubscriptionApprovalRequiredFlagChange) other).topicName)
                && subscriptionApprovalRequired == ((UpdateSubscriptionApprovalRequiredFlagChange) other).subscriptionApprovalRequired;
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        return context.getTopicService().setSubscriptionApprovalRequiredFlag(context.getTargetEnvironmentId(),
                topicName, subscriptionApprovalRequired);
    }

}

@JsonSerialize
final class PublishTopicSchemaVersionChange extends ChangeBase {

    private final String topicName;

    private final SchemaMetadata schemaMetadata;

    public PublishTopicSchemaVersionChange(String topicName, SchemaMetadata schemaMetadata) {
        super(ChangeType.TOPIC_SCHEMA_VERSION_PUBLISHED);
        this.topicName = topicName;
        this.schemaMetadata = schemaMetadata;
    }

    public String getTopicName() {
        return topicName;
    }

    public SchemaMetadata getSchemaMetadata() {
        return schemaMetadata;
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        PublishTopicSchemaVersionChange change = (PublishTopicSchemaVersionChange) other;
        return Objects.equals(topicName, change.topicName)
                && schemaMetadata.getSchemaVersion() == change.schemaMetadata.getSchemaVersion();
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        return context.getTopicService().addTopicSchemaVersion(context.getTargetEnvironmentId(), schemaMetadata);
    }

}

@JsonSerialize
final class DeleteTopicSchemaVersionChange extends ChangeBase {

    private final String topicName;

    public DeleteTopicSchemaVersionChange(String topicName) {
        super(ChangeType.TOPIC_SCHEMA_VERSION_DELETED);
        this.topicName = topicName;
    }

    public String getTopicName() {
        return topicName;
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        PublishTopicSchemaVersionChange change = (PublishTopicSchemaVersionChange) other;
        return Objects.equals(topicName, change.getTopicName());
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        throw new UnsupportedOperationException("Cannot apply changes to deleted Schema");
    }

}

@JsonSerialize
final class RegisterApplicationChange extends ChangeBase {

    private final String applicationId;

    private final ApplicationMetadata applicationMetadata;

    @SuppressWarnings("deprecation")
    public RegisterApplicationChange(String applicationId, ApplicationMetadata applicationMetadata) {
        super(ChangeType.APPLICATION_REGISTERED);
        this.applicationId = applicationId;
        this.applicationMetadata = applicationMetadata;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public ApplicationMetadata getApplicationMetadata() {
        return applicationMetadata;
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        // will never be used during staging (and should not be used at all) - so never equal to anything
        return false;
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        throw new UnsupportedOperationException("Application registrations cannot be staged");
    }

}

@JsonSerialize
final class CompoundChange extends ChangeBase {

    private final ChangeBase mainChange;

    private final List<ChangeBase> additionalChanges;

    public CompoundChange(ChangeBase mainChange, List<ChangeBase> additionalChanges) {
        super(ChangeType.COMPOUND_CHANGE);
        this.mainChange = mainChange;
        this.additionalChanges = new ArrayList<>(additionalChanges);
    }

    public ChangeBase getMainChange() {
        return mainChange;
    }

    public List<ChangeBase> getAdditionalChanges() {
        return additionalChanges;
    }

    @Override
    protected boolean isEqualTo(ChangeBase other) {
        CompoundChange change = (CompoundChange) other;
        return mainChange.equals(change.mainChange) && additionalChanges.equals(change.additionalChanges);
    }

    @Override
    public CompletableFuture<?> applyTo(ApplyChangeContext context) {
        CompletableFuture<?> result = mainChange.applyTo(context);
        for (ChangeBase change : additionalChanges) {
            result = result.thenCompose(o -> change.applyTo(context));
        }
        return result;
    }

}
