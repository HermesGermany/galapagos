package com.hermesworld.ais.galapagos.kafka.config;

public interface KafkaEnvironmentConfig {

    String getId();

    String getName();

    String getBootstrapServers();

    /**
     * If <code>true</code>, direct changes on this environment are not allowed (topics / subscriptions). You must use
     * staging functionality from the preceding environment stage to get changes on this environment.
     *
     * @return <code>true</code> if no direct changes are allowed on this environment, <code>false</code> otherwise.
     */
    boolean isStagingOnly();

    String getAuthenticationMode();

}
