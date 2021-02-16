package com.hermesworld.ais.galapagos.naming.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@ConfigurationPropertiesBinding
public class CaseStrategyConverterBinding implements Converter<String, CaseStrategy> {

    @Override
    public CaseStrategy convert(@NonNull String source) {
        return Arrays.stream(CaseStrategy.values()).filter(strategy -> strategy.configValue().equals(source)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Invalid case strategy: " + source));
    }
}
