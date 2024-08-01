package com.hermesworld.ais.galapagos.naming.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties("galapagos.naming")
@Validated
@Setter
@Getter
public class NamingConfig implements Validator {

    public static final String PARAM_APPLICATION = "{application}";

    public static final String PARAM_APP_OR_ALIAS = "{app-or-alias}";

    public static final String PARAM_BUSINESS_CAPABILITY = "{business-capability}";

    public static final String PARAM_ADDITION = "{addition}";

    public static final String KAFKA_VALID_NAMES_REGEX = "[a-zA-Z0-9._\\-]+";

    private TopicNamingConfig events = new TopicNamingConfig();

    private TopicNamingConfig commands = new TopicNamingConfig();

    private TopicNamingConfig data = new TopicNamingConfig();

    private String internalTopicPrefixFormat;

    private String consumerGroupPrefixFormat;

    private String transactionalIdPrefixFormat;

    private boolean allowInternalTopicNamesAsConsumerGroups;

    private CaseStrategy normalizationStrategy;

    @Override
    public boolean supports(@NonNull Class<?> clazz) {
        return clazz == String.class || clazz == AdditionNamingRules.class;
    }

    @Override
    public void validate(@NonNull Object target, @NonNull Errors errors) {
        String objName = errors.getObjectName();

        if (target instanceof String && objName.endsWith("-format")) {
            checkValidFormat(target.toString(), errors);
        }
        else if (target instanceof AdditionNamingRules rules) {
            if (StringUtils.hasLength(rules.getAllowedSeparators())
                    && !rules.getAllowedSeparators().matches(KAFKA_VALID_NAMES_REGEX)) {
                errors.rejectValue("allowedSeparators", "invalid.value",
                        "The separators must be valid for use in Kafka Topic Names. Only dots, underscores, and hyphens are allowed.");
            }
        }
    }

    private void checkValidFormat(String format, Errors errors) {
        if (format.contains(PARAM_ADDITION) && !format.endsWith(PARAM_ADDITION)) {
            errors.rejectValue(null, "invalid.addition.position",
                    errors.getObjectName() + " must END with placeholder " + PARAM_ADDITION);
            return;
        }

        format = format.replace(PARAM_APPLICATION, "test").replace(PARAM_APP_OR_ALIAS, "test")
                .replace(PARAM_BUSINESS_CAPABILITY, "test").replace(PARAM_ADDITION, "test");

        if (!format.matches(KAFKA_VALID_NAMES_REGEX)) {
            errors.rejectValue(null, "invalid.format",
                    errors.getObjectName() + " contains invalid characters for Kafka object names.");
        }
    }
}
