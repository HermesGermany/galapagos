package com.hermesworld.ais.galapagos.naming;

import com.hermesworld.ais.galapagos.applications.BusinessCapability;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.topics.TopicType;

/**
 * Service for determining and validating names of Kafka objects like Topics, Consumer Groups, and Transactional IDs.
 */
public interface NamingService {
    /**
     * Normalizes the given name, for use in naming objects (but not e.g. for use in certificate DNs). Adheres to the
     * configured normalization strategy. Localized special characters like Ä, Ö, É etc are normalized using ICU4J and
     * the <code>de-ASCII</code> transliteration. Spaces and special characters are treated as word separators. Adjacent
     * word separators are combined to one. The normalization strategy determines how the resulting words are combined.
     *
     * @param name Name to normalize for use in naming objects.
     * @return Normalized Name.
     * @throws NullPointerException If name is <code>null</code>.
     */
    String normalize(String name);

    String getTopicNameSuggestion(TopicType topicType, KnownApplication application, BusinessCapability capability);

    ApplicationPrefixes getAllowedPrefixes(KnownApplication application);

    void validateTopicName(String topicName, TopicType topicType, KnownApplication application)
            throws InvalidTopicNameException;

}
