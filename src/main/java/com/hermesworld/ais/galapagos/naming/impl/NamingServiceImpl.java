package com.hermesworld.ais.galapagos.naming.impl;

import com.hermesworld.ais.galapagos.applications.BusinessCapability;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.naming.ApplicationPrefixes;
import com.hermesworld.ais.galapagos.naming.InvalidTopicNameException;
import com.hermesworld.ais.galapagos.naming.NamingService;
import com.hermesworld.ais.galapagos.naming.config.AdditionNamingRules;
import com.hermesworld.ais.galapagos.naming.config.CaseStrategy;
import com.hermesworld.ais.galapagos.naming.config.NamingConfig;
import com.hermesworld.ais.galapagos.naming.config.TopicNamingConfig;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.ibm.icu.text.Transliterator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class NamingServiceImpl implements NamingService {

    private static final String DEFAULT_PLACEHOLDER = "My Topic";

    private final NamingConfig config;

    public NamingServiceImpl(NamingConfig config) {
        this.config = config;
    }

    @Override
    public String getTopicNameSuggestion(TopicType topicType, KnownApplication application,
            BusinessCapability capability) {
        if (topicType == TopicType.INTERNAL) {
            return getAllowedPrefixes(application).getInternalTopicPrefixes().get(0) + normalize(DEFAULT_PLACEHOLDER);
        }

        if (capability == null) {
            return null;
        }

        TopicNamingConfig namingConfig = getTopicNamingConfig(topicType);
        if (namingConfig == null) {
            return null;
        }

        return formatSingle(namingConfig.getNameFormat(), application, capability).replace(NamingConfig.PARAM_ADDITION,
                normalize(DEFAULT_PLACEHOLDER));
    }

    @Override
    public ApplicationPrefixes getAllowedPrefixes(KnownApplication application) {
        List<String> internalTopicPrefixes = formatMulti(config.getInternalTopicPrefixFormat(), application, null);
        List<String> consumerGroupPrefixes = formatMulti(config.getConsumerGroupPrefixFormat(), application, null);
        if (config.isAllowInternalTopicNamesAsConsumerGroups()) {
            consumerGroupPrefixes = Stream.concat(consumerGroupPrefixes.stream(), internalTopicPrefixes.stream())
                    .distinct().collect(Collectors.toList());
        }

        return new ApplicationPrefixesImpl(internalTopicPrefixes, consumerGroupPrefixes,
                formatMulti(config.getTransactionalIdPrefixFormat(), application, null));
    }

    @Override
    public void validateTopicName(String topicName, TopicType topicType, KnownApplication application)
            throws InvalidTopicNameException {
        // first of all, must be a valid Kafka Topic Name!
        if (topicName.length() > 249) {
            throw new InvalidTopicNameException("Topic name too long! Max length is 249 characters");
        }
        if (!topicName.matches(NamingConfig.KAFKA_VALID_NAMES_REGEX)) {
            throw new InvalidTopicNameException("Invalid Kafka Topic Name");
        }

        if (topicType == TopicType.INTERNAL) {
            List<String> prefixes = getAllowedPrefixes(application).getInternalTopicPrefixes();
            if (prefixes.stream().noneMatch(prefix -> topicName.startsWith(prefix))) {
                throw new InvalidTopicNameException("Wrong prefix used for internal topic of this application");
            }

            // OK; everything else does not matter for us
            return;
        }

        TopicNamingConfig namingConfig = getTopicNamingConfig(topicType);
        if (namingConfig == null) {
            throw new IllegalArgumentException("Invalid Topic Type: " + topicType);
        }

        boolean anyMatch = false;
        for (BusinessCapability capability : application.getBusinessCapabilities()) {
            List<String> allowedFormats = formatMulti(namingConfig.getNameFormat(), application, capability);
            anyMatch = allowedFormats.stream()
                    .anyMatch(format -> matchesFormat(topicName, format, namingConfig.getAdditionRules()));
            if (anyMatch) {
                break;
            }
        }

        if (!anyMatch) {
            throw new InvalidTopicNameException("Invalid topic name: Does not adhere to topic naming convention");
        }
    }

    @Override
    public String normalize(String name) {
        CaseStrategy strategy = config.getNormalizationStrategy();
        if (strategy == null) {
            strategy = CaseStrategy.KEBAB_CASE;
        }

        List<String> words = extractWords(Transliterator.getInstance("de-ASCII").transliterate(name));
        return strategy.format(words);
    }

    private boolean matchesFormat(String topicName, String format, AdditionNamingRules additionRules) {
        if (!format.contains(NamingConfig.PARAM_ADDITION)) {
            // this must be a bad format - only one topic name per application or business capability?!
            return false;
        }
        String commonPrefix = format.substring(0, format.indexOf(NamingConfig.PARAM_ADDITION));
        if (!topicName.startsWith(commonPrefix)) {
            return false;
        }

        String addition = topicName.substring(commonPrefix.length());

        List<String> sections = StringUtils.isEmpty(additionRules.getAllowedSeparators()) ? List.of(addition)
                : Arrays.asList(addition.split("[" + additionRules.getAllowedSeparators().replace("-", "\\-") + "]"));

        for (String section : sections) {
            if (!matchesSectionRules(section, additionRules)) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesSectionRules(String section, AdditionNamingRules rules) {
        if (CaseStrategy.PASCAL_CASE.matches(section) && rules.isAllowPascalCase()) {
            return true;
        }
        if (CaseStrategy.CAMEL_CASE.matches(section) && rules.isAllowCamelCase()) {
            return true;
        }
        if (CaseStrategy.KEBAB_CASE.matches(section) && rules.isAllowKebabCase()) {
            return true;
        }
        if (CaseStrategy.SNAKE_CASE.matches(section) && rules.isAllowSnakeCase()) {
            return true;
        }

        // Lowercase is OK, but only if NO other case is allowed (note that lowercase would be a subset of camelCase and
        // kebab-case, so no need to check them here)
        return CaseStrategy.LOWERCASE.matches(section) && !rules.isAllowSnakeCase() && !rules.isAllowCamelCase();
    }

    private String formatSingle(String formatString, KnownApplication application, BusinessCapability capability) {
        if (formatString.contains(NamingConfig.PARAM_APP_OR_ALIAS)) {
            formatString = formatString.replace(NamingConfig.PARAM_APP_OR_ALIAS, NamingConfig.PARAM_APPLICATION);
        }
        return formatString.replace(NamingConfig.PARAM_APPLICATION, normalize(application.getName())).replace(
                NamingConfig.PARAM_BUSINESS_CAPABILITY, capability == null ? "" : normalize(capability.getName()));
    }

    private List<String> formatMulti(String formatString, KnownApplication application, BusinessCapability capability) {
        if (!formatString.contains(NamingConfig.PARAM_APP_OR_ALIAS)) {
            return List.of(formatSingle(formatString, application, capability));
        }

        List<String> appOrAlias = new ArrayList<>();
        appOrAlias.add(normalize(application.getName()));
        application.getAliases().stream().map(alias -> normalize(alias)).forEach(appOrAlias::add);

        return appOrAlias.stream().map(name -> formatString.replace(NamingConfig.PARAM_APPLICATION, appOrAlias.get(0))
                .replace(NamingConfig.PARAM_APP_OR_ALIAS, name)).collect(Collectors.toList());
    }

    private List<String> extractWords(String name) {
        StringBuilder currentWord = new StringBuilder();
        List<String> result = new ArrayList<>();

        Runnable addWord = () -> {
            if (currentWord.length() > 0) {
                result.add(currentWord.toString());
                currentWord.setLength(0);
            }
        };

        for (int i = 0; i < name.length(); i++) {
            if (Character.isLetter(name.charAt(i)) || Character.isDigit(name.charAt(i))) {
                currentWord.append(name.charAt(i));
            }
            else {
                addWord.run();
            }
        }
        addWord.run();

        return result;
    }

    private TopicNamingConfig getTopicNamingConfig(TopicType topicType) {
        switch (topicType) {
        case EVENTS:
            return config.getEvents();
        case DATA:
            return config.getData();
        case COMMANDS:
            return config.getCommands();
        default:
            return null;
        }
    }

}
