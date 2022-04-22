package com.hermesworld.ais.galapagos.schema;

import com.hermesworld.ais.galapagos.schemas.IncompatibleSchemaException;
import com.hermesworld.ais.galapagos.schemas.SchemaUtil;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.junit.Test;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SchemaUtilTest {

    @Test(expected = IncompatibleSchemaException.class)
    public void testAddAdditionalPropertiesOnObject_fail() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test01a"), readSchema("test01b"));
    }

    @Test
    public void testAddRequiredPropertyOnObject_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test02a"), readSchema("test02b"));
    }

    @Test
    public void testRemoveOneOfSchema_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test03a"), readSchema("test03b"));
    }

    @Test(expected = IncompatibleSchemaException.class)
    public void testAddOneOfSchema_fail() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test03a"), readSchema("test03c"));
    }

    @Test
    public void testAddArrayRestriction_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test03a"), readSchema("test03d"));
    }

    @Test(expected = IncompatibleSchemaException.class)
    public void testRelaxArrayRestriction_fail() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test03d"), readSchema("test03e"));
    }

    @Test
    public void testAddPropertyWithAdditionalProperties_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test04a"), readSchema("test04b"));
    }

    @Test
    public void testAddStringLimits_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test04a"), readSchema("test04c"));
    }

    @Test(expected = IncompatibleSchemaException.class)
    public void testRelaxStringLimits_fail() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test04c"), readSchema("test04d"));
    }

    @Test
    public void testRemoveEnumValue_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test05a"), readSchema("test05b"));
    }

    @Test(expected = IncompatibleSchemaException.class)
    public void testAddEnumValue_fail() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test05a"), readSchema("test05c"));
    }

    @Test
    public void testNotMoreGreedy_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test06a"), readSchema("test06b"));
    }

    @Test(expected = IncompatibleSchemaException.class)
    public void testNotMoreStrict_fail() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test06a"), readSchema("test06c"));
    }

    @Test(expected = IncompatibleSchemaException.class)
    public void testTotallyDifferent_fail() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test01a"), readSchema("test03a"));
    }

    @Test
    public void testAnyOfReplacedBySubschema_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test07a"), readSchema("test07b"));
    }

    @Test(expected = IncompatibleSchemaException.class)
    public void testAnyOfReplacedByIncompatibleSchema_fail() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test07a"), readSchema("test07c"));
    }

    @Test(expected = IncompatibleSchemaException.class)
    public void testIntegerToNumber_fail() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test08a"), readSchema("test08b"));
    }

    @Test
    public void testIntegerStaysInteger_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test08a"), readSchema("test08c"));
    }

    @Test
    public void testNumberToInteger_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test08b"), readSchema("test08a"));
    }

    @Test
    public void testRemoveOptionalWithNoAdditionalProperties_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test09a"), readSchema("test09b"));
    }

    @Test(expected = IncompatibleSchemaException.class)
    public void testRemoveOptionalWithAdditionalProperties_fail() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test09a"), readSchema("test09c"));
    }

    @Test
    public void testPatternField_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test-pattern-field"),
                readSchema("test-pattern-field-with-another-prop"));
    }

    private static Schema readSchema(String id) {
        try (InputStream in = SchemaUtilTest.class.getClassLoader()
                .getResourceAsStream("schema-compatibility/" + id + ".schema.json")) {
            String data = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(data);
            return SchemaLoader.load(obj);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
