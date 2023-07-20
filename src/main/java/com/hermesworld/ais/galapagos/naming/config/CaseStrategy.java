package com.hermesworld.ais.galapagos.naming.config;

import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The different Case strategies which can be used at multiple locations in the naming configuration. <br>
 * Each strategy can be used to validate a given String against this strategy, and to format a list of words according
 * to this strategy. <br>
 * See <code>application.properties</code> for examples and default values.
 */
public enum CaseStrategy {

    PASCAL_CASE("PascalCase", "([A-Z][a-z0-9]+)+",
            words -> words.stream().map(word -> StringUtils.capitalize(word.toLowerCase(Locale.US)))
                    .collect(Collectors.joining())),
    CAMEL_CASE("camelCase", "[a-z]+[a-z0-9]*([A-Z][a-z0-9]+)*",
            words -> words.get(0).toLowerCase(Locale.US) + words.subList(1, words.size()).stream()
                    .map(word -> StringUtils.capitalize(word.toLowerCase(Locale.US))).collect(Collectors.joining())),
    KEBAB_CASE("kebab-case", "([a-z][a-z0-9]*|[a-z][a-z0-9]*\\-[a-z0-9]*)+",
            words -> words.stream().map(word -> word.toLowerCase(Locale.US)).collect(Collectors.joining("-"))),
    LOWERCASE("lowercase", "[a-z][a-z0-9]*",
            words -> words.stream().map(word -> word.toLowerCase(Locale.US)).collect(Collectors.joining())),
    SNAKE_CASE("SNAKE_CASE", "([A-Z][A-Z0-9]*|[A-Z][A-Z0-9]*_[A-Z0-9]*)+",
            words -> words.stream().map(word -> word.toUpperCase(Locale.US)).collect(Collectors.joining("_")));

    private final String configValue;

    private final String validatorRegex;

    private final Function<List<String>, String> formatter;

    CaseStrategy(String configValue, String validatorRegex, Function<List<String>, String> formatter) {
        this.configValue = configValue;
        this.validatorRegex = validatorRegex;
        this.formatter = formatter;
    }

    public String configValue() {
        return configValue;
    }

    public boolean matches(String input) {
        if (ObjectUtils.isEmpty(input)) {
            return false;
        }
        return input.matches(validatorRegex);
    }

    public String format(List<String> words) {
        return formatter.apply(words);
    }

}
