package com.hermesworld.ais.galapagos.schema;

import com.hermesworld.ais.galapagos.schemas.IncompatibleSchemaException;
import com.hermesworld.ais.galapagos.schemas.SchemaUtil;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaUtilTest {

    @Test
    void testAddAdditionalPropertiesOnObject_fail() throws Exception {
        assertThrows(IncompatibleSchemaException.class, () -> {
            SchemaUtil.verifyCompatibleTo(readSchema("test01a"), readSchema("test01b"));
        });
    }

    @Test
    void testAddRequiredPropertyOnObject_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test02a"), readSchema("test02b"));
    }

    @Test
    void testRemoveOneOfSchema_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test03a"), readSchema("test03b"));
    }

    @Test
    void testAddOneOfSchema_fail() throws Exception {
        assertThrows(IncompatibleSchemaException.class, () -> {
            SchemaUtil.verifyCompatibleTo(readSchema("test03a"), readSchema("test03c"));
        });
    }

    @Test
    void testAddArrayRestriction_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test03a"), readSchema("test03d"));
    }

    @Test
    void testRelaxArrayRestriction_fail() throws Exception {
        assertThrows(IncompatibleSchemaException.class, () -> {
            SchemaUtil.verifyCompatibleTo(readSchema("test03d"), readSchema("test03e"));
        });
    }

    @Test
    void testAddPropertyWithAdditionalProperties_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test04a"), readSchema("test04b"));
    }

    @Test
    void testAddStringLimits_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test04a"), readSchema("test04c"));
    }

    @Test
    void testRelaxStringLimits_fail() throws Exception {
        assertThrows(IncompatibleSchemaException.class, () -> {
            SchemaUtil.verifyCompatibleTo(readSchema("test04c"), readSchema("test04d"));
        });
    }

    @Test
    void testRemoveEnumValue_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test05a"), readSchema("test05b"));
    }

    @Test
    void testAddEnumValue_fail() throws Exception {
        assertThrows(IncompatibleSchemaException.class, () -> {
            SchemaUtil.verifyCompatibleTo(readSchema("test05a"), readSchema("test05c"));
        });
    }

    @Test
    void testNotMoreGreedy_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test06a"), readSchema("test06b"));
    }

    @Test
    void testNotMoreStrict_fail() throws Exception {
        assertThrows(IncompatibleSchemaException.class, () -> {
            SchemaUtil.verifyCompatibleTo(readSchema("test06a"), readSchema("test06c"));
        });
    }

    @Test
    void testTotallyDifferent_fail() throws Exception {
        assertThrows(IncompatibleSchemaException.class, () -> {
            SchemaUtil.verifyCompatibleTo(readSchema("test01a"), readSchema("test03a"));
        });
    }

    @Test
    void testAnyOfReplacedBySubschema_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test07a"), readSchema("test07b"));
    }

    @Test
    void testAnyOfReplacedByIncompatibleSchema_fail() throws Exception {
        assertThrows(IncompatibleSchemaException.class, () -> {
            SchemaUtil.verifyCompatibleTo(readSchema("test07a"), readSchema("test07c"));
        });
    }

    @Test
    void testIntegerToNumber_fail() throws Exception {
        assertThrows(IncompatibleSchemaException.class, () -> {
            SchemaUtil.verifyCompatibleTo(readSchema("test08a"), readSchema("test08b"));
        });
    }

    @Test
    void testIntegerStaysInteger_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test08a"), readSchema("test08c"));
    }

    @Test
    void testNumberToInteger_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test08b"), readSchema("test08a"));
    }

    @Test
    void testRemoveOptionalWithNoAdditionalProperties_success() throws Exception {
        SchemaUtil.verifyCompatibleTo(readSchema("test09a"), readSchema("test09b"));
    }

    @Test
    void testRemoveOptionalWithAdditionalProperties_fail() throws Exception {
        assertThrows(IncompatibleSchemaException.class, () -> {
            SchemaUtil.verifyCompatibleTo(readSchema("test09a"), readSchema("test09c"));
        });
    }

    @Test
    void testPatternField_success() throws Exception {
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
