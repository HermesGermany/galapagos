package com.hermesworld.ais.galapagos.naming.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CaseStrategyConverterBindingTest {

    @Test
    public void testValidValues() {
        CaseStrategyConverterBinding converter = new CaseStrategyConverterBinding();
        assertEquals(CaseStrategy.PASCAL_CASE, converter.convert("PascalCase"));
        assertEquals(CaseStrategy.CAMEL_CASE, converter.convert("camelCase"));
        assertEquals(CaseStrategy.KEBAB_CASE, converter.convert("kebab-case"));
        assertEquals(CaseStrategy.SNAKE_CASE, converter.convert("SNAKE_CASE"));
        assertEquals(CaseStrategy.LOWERCASE, converter.convert("lowercase"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoCaseConversion() {
        CaseStrategyConverterBinding converter = new CaseStrategyConverterBinding();
        converter.convert("pascalCase");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoEnumNameUse() {
        CaseStrategyConverterBinding converter = new CaseStrategyConverterBinding();
        converter.convert("PASCAL_CASE");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidValue() {
        CaseStrategyConverterBinding converter = new CaseStrategyConverterBinding();
        converter.convert("someValue");
    }

}
