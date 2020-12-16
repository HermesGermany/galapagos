package com.hermesworld.ais.galapagos.topics;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.BusinessCapability;
import com.hermesworld.ais.galapagos.applications.KnownApplication;

/**
 * Customization interface for defining a Topic Name Validation strategy. A default implementation is included in
 * Galapagos, but implementors could provide their own strategy using this interface.
 *
 * @author AlbrechtFlo
 *
 */
public interface TopicNameValidator {

	void validateTopicName(String topicName, TopicType topicType, KnownApplication ownerApplication,
			ApplicationMetadata applicationMetadata)
			throws InvalidTopicNameException;

	String getTopicNameSuggestion(TopicType topicType, KnownApplication ownerApplication, ApplicationMetadata applicationMetadata,
			BusinessCapability businessCapability);

}
