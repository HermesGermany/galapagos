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

    /**
     * If <code>true</code>, application ACLs will <b>not</b> be updated on this environment. You will have to provide
     * external means to have correct ACLs in place. This can be useful e.g. in migration scenarios, where ACLs are
     * synchronized outside Galapagos, and you do not want to override these based on e.g. yet incomplete metadata.
     *
     * @return <code>true</code> if application ACLs shall not be updated on this environment, <code>false</code>
     *         otherwise.
     */
    boolean isNoUpdateApplicationAcls();

    /**
     * If <code>true</code>, developer authentications on this environment will receive not only all read access of
     * their assigned applications, but also their write access. This can be useful for manual insertion of test data i
     *
     * @return <code>true</code> if developer authentications shall receive write access on this environment (depending
     *         on associated applications), <code>false</code> otherwise.
     */
    boolean isDeveloperWriteAccess();

}
