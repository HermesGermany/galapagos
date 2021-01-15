package com.hermesworld.ais.galapagos.kafka.impl;

import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.util.HasKey;

public interface KafkaRepositoryContainer {

    <T extends HasKey> TopicBasedRepository<T> addRepository(String topicName, Class<T> valueClass);

}
