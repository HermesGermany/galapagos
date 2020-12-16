package com.hermesworld.ais.galapagos.changes;

import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.service.TopicService;

public interface ApplyChangeContext {

	public String getTargetEnvironmentId();

	public TopicService getTopicService();

	public SubscriptionService getSubscriptionService();

}
