package com.hermesworld.ais.galapagos.kafka.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.common.config.TopicConfig;

/**
 * Helper class for dealing with Kafka Topic Config properties. This class contains lots of information from the Kafka
 * docs, e.g. default values for some properties, and property precedence (e.g. <code>log.retention.hours</code> vs.
 * <code>log.retention.ms</code>...)
 *
 * @author AlbrechtFlo
 *
 */
public class KafkaTopicConfigHelper {

    private static final Map<String, ConfigValueInfo> CONFIG_INFOS = new LinkedHashMap<>();

    private static final Map<String, List<SecondaryServerProp>> SECONDARY_SERVER_PROPS = new HashMap<>();

    private static final String INFINITE_MS = "9223372036854775807";

    private static final Function<String, String> hoursToMillis = (hours) -> String
            .valueOf(TimeUnit.HOURS.toMillis(Long.valueOf(hours, 10)));

    static {
        CONFIG_INFOS.put(TopicConfig.CLEANUP_POLICY_CONFIG,
                new ConfigValueInfo("delete", "log.cleanup.policy", TopicConfig.CLEANUP_POLICY_DOC));
        CONFIG_INFOS.put(TopicConfig.RETENTION_MS_CONFIG,
                new ConfigValueInfo("604800000", "log.retention.ms", TopicConfig.RETENTION_MS_DOC));
        CONFIG_INFOS.put(TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG,
                new ConfigValueInfo("0", "log.cleaner.min.compaction.lag.ms", TopicConfig.MIN_COMPACTION_LAG_MS_DOC));
        CONFIG_INFOS.put(TopicConfig.DELETE_RETENTION_MS_CONFIG, new ConfigValueInfo("86400000",
                "log.cleaner.delete.retention.ms", TopicConfig.DELETE_RETENTION_MS_DOC));
        CONFIG_INFOS.put(TopicConfig.COMPRESSION_TYPE_CONFIG,
                new ConfigValueInfo("producer", "compression.type", TopicConfig.COMPRESSION_TYPE_DOC));
        CONFIG_INFOS.put(TopicConfig.FILE_DELETE_DELAY_MS_CONFIG,
                new ConfigValueInfo("60000", "log.segment.delete.delay.ms", TopicConfig.FILE_DELETE_DELAY_MS_DOC));
        CONFIG_INFOS.put(TopicConfig.FLUSH_MESSAGES_INTERVAL_CONFIG, new ConfigValueInfo(INFINITE_MS,
                "log.flush.interval.messages", TopicConfig.FLUSH_MESSAGES_INTERVAL_DOC));
        CONFIG_INFOS.put(TopicConfig.FLUSH_MS_CONFIG,
                new ConfigValueInfo(INFINITE_MS, "log.flush.interval.ms", TopicConfig.FLUSH_MS_DOC));
        CONFIG_INFOS.put(TopicConfig.INDEX_INTERVAL_BYTES_CONFIG,
                new ConfigValueInfo("4096", "log.index.interval.bytes", TopicConfig.INDEX_INTERVAL_BYTES_DOCS));
        CONFIG_INFOS.put(TopicConfig.MAX_MESSAGE_BYTES_CONFIG,
                new ConfigValueInfo("1000012", "message.max.bytes", TopicConfig.MAX_MESSAGE_BYTES_DOC));
        CONFIG_INFOS.put(TopicConfig.MESSAGE_FORMAT_VERSION_CONFIG,
                new ConfigValueInfo(null, "log.message.format.version", TopicConfig.MESSAGE_FORMAT_VERSION_DOC));
        CONFIG_INFOS.put(TopicConfig.MESSAGE_TIMESTAMP_DIFFERENCE_MAX_MS_CONFIG, new ConfigValueInfo(INFINITE_MS,
                "log.message.timestamp.difference.max.ms", TopicConfig.MESSAGE_TIMESTAMP_DIFFERENCE_MAX_MS_DOC));
        CONFIG_INFOS.put(TopicConfig.MESSAGE_TIMESTAMP_TYPE_CONFIG, new ConfigValueInfo("CreateTime",
                "log.message.timestamp.type", TopicConfig.MESSAGE_TIMESTAMP_TYPE_DOC));
        CONFIG_INFOS.put(TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG, new ConfigValueInfo("0.5",
                "log.cleaner.min.cleanable.ratio", TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_DOC));
        CONFIG_INFOS.put(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG,
                new ConfigValueInfo("1", "min.insync.replicas", TopicConfig.MIN_IN_SYNC_REPLICAS_DOC));
        CONFIG_INFOS.put(TopicConfig.PREALLOCATE_CONFIG,
                new ConfigValueInfo("false", "log.preallocate", TopicConfig.PREALLOCATE_DOC));
        CONFIG_INFOS.put(TopicConfig.RETENTION_BYTES_CONFIG,
                new ConfigValueInfo("-1", "log.retention.bytes", TopicConfig.RETENTION_BYTES_DOC));
        CONFIG_INFOS.put(TopicConfig.SEGMENT_BYTES_CONFIG,
                new ConfigValueInfo("1073741824", "log.segment.bytes", TopicConfig.SEGMENT_BYTES_DOC));
        CONFIG_INFOS.put(TopicConfig.SEGMENT_INDEX_BYTES_CONFIG,
                new ConfigValueInfo("10485760", "log.index.size.max.bytes", TopicConfig.SEGMENT_INDEX_BYTES_DOC));
        CONFIG_INFOS.put(TopicConfig.SEGMENT_JITTER_MS_CONFIG,
                new ConfigValueInfo("0", "log.roll.jitter.ms", TopicConfig.SEGMENT_JITTER_MS_DOC));
        CONFIG_INFOS.put(TopicConfig.SEGMENT_MS_CONFIG,
                new ConfigValueInfo("604800000", "log.roll.ms", TopicConfig.SEGMENT_MS_DOC));
        CONFIG_INFOS.put(TopicConfig.UNCLEAN_LEADER_ELECTION_ENABLE_CONFIG, new ConfigValueInfo("false",
                "unclean.leader.election.enable", TopicConfig.UNCLEAN_LEADER_ELECTION_ENABLE_DOC));
        CONFIG_INFOS.put(TopicConfig.MESSAGE_DOWNCONVERSION_ENABLE_CONFIG, new ConfigValueInfo("true",
                "log.message.downconversion.enable", TopicConfig.MESSAGE_DOWNCONVERSION_ENABLE_DOC));

        SECONDARY_SERVER_PROPS
                .put(TopicConfig.RETENTION_MS_CONFIG,
                        Arrays.asList(
                                new SecondaryServerProp("log.retention.minutes",
                                        (minutes) -> String
                                                .valueOf(TimeUnit.MINUTES.toMillis(Long.valueOf(minutes, 10)))),
                                new SecondaryServerProp("log.retention.hours", hoursToMillis)));
        SECONDARY_SERVER_PROPS.put(TopicConfig.SEGMENT_MS_CONFIG,
                List.of(new SecondaryServerProp("log.roll.hours", hoursToMillis)));
        SECONDARY_SERVER_PROPS.put(TopicConfig.SEGMENT_JITTER_MS_CONFIG,
                List.of(new SecondaryServerProp("log.roll.jitter.hours", hoursToMillis)));
    }

    private KafkaTopicConfigHelper() {
    }

    public static Map<String, String> getConfigKeysAndDescription() {
        Map<String, String> result = new LinkedHashMap<>();
        CONFIG_INFOS.entrySet().stream().forEach(e -> result.put(e.getKey(), e.getValue().getDescription()));
        return result;
    }

    public static Map<String, String> getTopicDefaultValues(Config brokerConfig) {
        Map<String, String> brokerConfigValues = new HashMap<>();
        // have to use forEach instead of Map collector because values could be null
        brokerConfig.entries().stream().forEach(entry -> brokerConfigValues.put(entry.name(), entry.value()));

        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, ConfigValueInfo> entry : CONFIG_INFOS.entrySet()) {
            String configKey = entry.getKey();
            ConfigValueInfo info = entry.getValue();

            String serverDefault = null;
            if (brokerConfigValues.containsKey(info.getServerDefaultProperty())) {
                serverDefault = brokerConfigValues.get(info.getServerDefaultProperty());
            }

            if (serverDefault == null && SECONDARY_SERVER_PROPS.containsKey(configKey)) {
                for (SecondaryServerProp prop : SECONDARY_SERVER_PROPS.get(configKey)) {
                    if (brokerConfigValues.get(prop.getConfigName()) != null) {
                        serverDefault = prop.apply(brokerConfigValues.get(prop.getConfigName()));
                        break;
                    }
                }
            }

            result.put(configKey, serverDefault == null ? info.getDefaultValue() : serverDefault);
        }

        return result;
    }

    private static class ConfigValueInfo {

        private String defaultValue;

        private String serverDefaultProperty;

        private String description;

        public ConfigValueInfo(String defaultValue, String serverDefaultProperty, String description) {
            this.defaultValue = defaultValue;
            this.serverDefaultProperty = serverDefaultProperty;
            this.description = description;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public String getServerDefaultProperty() {
            return serverDefaultProperty;
        }

        public String getDescription() {
            return description;
        }

    }

    private static class SecondaryServerProp {

        private String configName;

        private Function<String, String> mappingFunction;

        public SecondaryServerProp(String configName, Function<String, String> mappingFunction) {
            this.configName = configName;
            this.mappingFunction = mappingFunction;
        }

        public String getConfigName() {
            return configName;
        }

        public String apply(String baseConfigValue) {
            return mappingFunction.apply(baseConfigValue);
        }

    }

}
