package com.hermesworld.ais.galapagos.schemas;

import org.everit.json.schema.Schema;

import java.util.Optional;

/**
 * Utility class to compare two schemas for Consumer compatibility. Guideline is that a potential consumer which
 * currently consumes data in <code>oldSchema</code> format should be able to also handle data in <code>newSchema</code>
 * format without any adjustments. <br>
 */
public final class SchemaUtil {

    private SchemaUtil() {
    }

    /**
     * Checks if two JSON schemas are equal. Equality is defined by the org.everit JSON schema library.
     *
     * @param schema1 Schema to compare.
     * @param schema2 Schema to compare.
     * @return <code>true</code> if both schemas are equal according to the definition of the library,
     *         <code>false</code> otherwise.
     */
    public static boolean areEqual(Schema schema1, Schema schema2) {
        return schema1.getClass() == schema2.getClass() && schema1.equals(schema2);
    }

    static String propLocationName(SchemaCompatibilityValidationContext context) {
        return Optional.ofNullable(context.getCurrentPrefix()).orElse("root");
    }

    static String fullPropName(SchemaCompatibilityValidationContext context, String property) {
        return Optional.ofNullable(context.getCurrentPrefix()).map(s -> s + "." + property).orElse(property);
    }

}
