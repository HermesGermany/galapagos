package com.hermesworld.ais.galapagos.topics.service;

/**
 * Interface of a TopicService which additionally checks "business" validation rules, e.g. deletion of topics only if no
 * subscribers, creation of topics only on non-staging-only environments etc. <br>
 * Note that the "plain" TopicService still does technical and access validations. <br>
 * The <code>TopicController</code> uses an implementation of this interface, while the "plain" {@link TopicService} is
 * used by the Staging engine. <br>
 * This interface is only a marker interface (for Dependency Injection) and does not add any new methods.
 *
 * @author AlbrechtFlo
 */
public interface ValidatingTopicService extends TopicService {
}
