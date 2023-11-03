package com.hermesworld.ais.galapagos.naming.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaseStrategyConverterBindingTest {

    @Test
    void testValidValues() {
        CaseStrategyConverterBinding converter = new CaseStrategyConverterBinding();
        assertEquals(CaseStrategy.PASCAL_CASE, converter.convert("PascalCase"));
        assertEquals(CaseStrategy.CAMEL_CASE, converter.convert("camelCase"));
        assertEquals(CaseStrategy.KEBAB_CASE, converter.convert("kebab-case"));
        assertEquals(CaseStrategy.SNAKE_CASE, converter.convert("SNAKE_CASE"));
        assertEquals(CaseStrategy.LOWERCASE, converter.convert("lowercase"));
    }

    @Test
    void testNoCaseConversion() {
        assertThrows(IllegalArgumentException.class, () -> {
            CaseStrategyConverterBinding converter = new CaseStrategyConverterBinding();
            converter.convert("pascalCase");
        });
    }

    @Test
    void testNoEnumNameUse() {
        assertThrows(IllegalArgumentException.class, () -> {
            CaseStrategyConverterBinding converter = new CaseStrategyConverterBinding();
            converter.convert("PASCAL_CASE");
        });
    }

    @Test
    void testInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> {
            CaseStrategyConverterBinding converter = new CaseStrategyConverterBinding();
            converter.convert("someValue");
        });
    }

}
