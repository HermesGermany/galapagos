package com.hermesworld.ais.galapagos.schemas;

import org.everit.json.schema.*;
import org.everit.json.schema.regexp.Regexp;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SchemaUtil {

    // Known limitations:

    // - Regex Pattern compatibility is not checked - every difference is treated as incompatible.
    // Theoretically, there could be stricter patterns which include the previous patterns.

    private SchemaUtil() {
    }

    public static boolean areEqual(Schema schema1, Schema schema2) {
        return schema1.getClass() == schema2.getClass() && schema1.equals(schema2);
    }

    public static void verifyCompatibleTo(Schema oldSchema, Schema newSchema) throws IncompatibleSchemaException {
        verifyCompatibleTo(oldSchema, newSchema, null);
    }

    private static void verifyCompatibleTo(Schema oldSchema, Schema newSchema, String prefix)
            throws IncompatibleSchemaException {
        if (oldSchema.getClass() == EmptySchema.class) {
            // always compatible
            return;
        }

        // special case: "anyOf" or "oneOf" can be replaced by one of its subschemas
        if (oldSchema.getClass() == CombinedSchema.class && newSchema.getClass() != CombinedSchema.class) {
            verifyCombinedReplacedBySubschema((CombinedSchema) oldSchema, newSchema, prefix);
            return;
        }

        if (newSchema.getClass() != oldSchema.getClass()) {
            throw new IncompatibleSchemaException("Schema type differs at " + propLocationName(prefix) + " (new: "
                    + newSchema.getClass().getSimpleName() + ", old: " + oldSchema.getClass().getSimpleName() + ")");
        }

        if (newSchema.getClass() == ObjectSchema.class) {
            verifyCompatibleTo((ObjectSchema) oldSchema, (ObjectSchema) newSchema, prefix);
        }
        else if (newSchema.getClass() == StringSchema.class) {
            verifyCompatibleTo((StringSchema) oldSchema, (StringSchema) newSchema, prefix);
        }
        else if (newSchema.getClass() == EnumSchema.class) {
            verifyCompatibleTo((EnumSchema) oldSchema, (EnumSchema) newSchema, prefix);
        }
        else if (newSchema.getClass() == ArraySchema.class) {
            verifyCompatibleTo((ArraySchema) oldSchema, (ArraySchema) newSchema, prefix);
        }
        else if (newSchema.getClass() == NumberSchema.class) {
            verifyCompatibleTo((NumberSchema) oldSchema, (NumberSchema) newSchema, prefix);
        }
        else if (newSchema.getClass() == CombinedSchema.class) {
            verifyCompatibleTo((CombinedSchema) oldSchema, (CombinedSchema) newSchema, prefix);
        }
        else if (newSchema.getClass() == ConditionalSchema.class) {
            verifyCompatibleTo((ConditionalSchema) oldSchema, (ConditionalSchema) newSchema, prefix);
        }
        else if (newSchema.getClass() == NotSchema.class) {
            verifyCompatibleTo((NotSchema) oldSchema, (NotSchema) newSchema, prefix);
        }
        else if (newSchema.getClass() == ConstSchema.class) {
            verifyCompatibleTo((ConstSchema) oldSchema, (ConstSchema) newSchema, prefix);
        }
        else if (newSchema.getClass() == ReferenceSchema.class) {
            verifyCompatibleTo((ReferenceSchema) oldSchema, (ReferenceSchema) newSchema, prefix);
        }
        else if (newSchema.getClass() == NullSchema.class || newSchema.getClass() == BooleanSchema.class) {
            // noinspection UnnecessaryReturnStatement
            return;
        }
        else {
            // unsupported (top) schema type
            throw new IncompatibleSchemaException(
                    "Unsupported schema type used: " + oldSchema.getClass().getSimpleName());
        }
    }

    private static void verifyCompatibleTo(ObjectSchema oldSchema, ObjectSchema newSchema, String prefix)
            throws IncompatibleSchemaException {
        if (!oldSchema.permitsAdditionalProperties() && newSchema.permitsAdditionalProperties()) {
            throw new IncompatibleSchemaException("""
                    additionalProperties must not be introduced \
                    in a newer schema version (old consumers could rely on no additional properties being present)\
                    """);
        }

        // required properties must still be required
        for (String property : oldSchema.getRequiredProperties()) {
            if (!newSchema.getRequiredProperties().contains(property)) {
                throw new IncompatibleSchemaException("Property " + fullPropName(prefix, property)
                        + " is no longer required, but old consumers could rely on it.");
            }
        }

        // min properties cannot be reduced (consumer could rely on having at least N
        // properties)
        if (oldSchema.getMinProperties() != null && (newSchema.getMinProperties() == null
                || newSchema.getMinProperties() < oldSchema.getMinProperties())) {
            throw new IncompatibleSchemaException("Minimum number of properties cannot be reduced at "
                    + propLocationName(prefix) + ". Old consumers could rely on having at least N properties.");
        }

        // Previously strongly typed (optional) properties must still be available with
        // same format
        oldPropsLoop: for (String property : oldSchema.getPropertySchemas().keySet()) {
            Schema oldPropSchema = oldSchema.getPropertySchemas().get(property);

            // simple case: still exists directly
            Schema newPropSchema = newSchema.getPropertySchemas().get(property);
            if (newPropSchema != null) {
                verifyCompatibleTo(oldPropSchema, newPropSchema, fullPropName(prefix, property));
                continue;
            }

            // could now be inside the dependencies (verify ALL occurrences to be compatible
            // to previous schema)
            boolean found = false;
            for (Schema newDepSchema : newSchema.getSchemaDependencies().values()) {
                if (!(newDepSchema instanceof ObjectSchema)) {
                    throw new IncompatibleSchemaException(
                            "Invalid dependencies configuration found at " + propLocationName(prefix));
                }
                newPropSchema = ((ObjectSchema) newDepSchema).getPropertySchemas().get(property);
                if (newPropSchema != null) {
                    verifyCompatibleTo(oldPropSchema, newPropSchema, fullPropName(prefix, property));
                    found = true;
                }
            }
            if (found) {
                continue;
            }

            // could now be covered by a Pattern
            Map<Regexp, Schema> newPatternSchemas = getPatternProperties(newSchema);
            for (Map.Entry<Regexp, Schema> entry : newPatternSchemas.entrySet()) {
                if (entry.getKey().patternMatchingFailure(property).isEmpty()) {
                    verifyCompatibleTo(oldPropSchema, entry.getValue(), fullPropName(prefix, property));
                    // JSON Schema logic: First matching pattern wins...
                    continue oldPropsLoop;
                }
            }

            // if no additionalProperties allowed in new schema, we are fine, because will not occur in different format
            if (!newSchema.permitsAdditionalProperties() && !oldSchema.getRequiredProperties().contains(property)) {
                continue;
            }

            // OK, defined nowhere, so we can't be sure that it will still be generated in
            // same form
            throw new IncompatibleSchemaException("Property " + fullPropName(prefix, property)
                    + " is no longer defined in schema, but had a strong definition before. "
                    + "Field format could have changed, and old consumers perhaps rely on previous format");
        }

        // strong assumption: If pattern properties were used, all patterns must still
        // exist, and still be compatible
        Map<Regexp, Schema> oldPatternSchemas = getPatternProperties(oldSchema);
        Map<Regexp, Schema> newPatternSchemas = getPatternProperties(newSchema);

        for (Map.Entry<Regexp, Schema> pattern : oldPatternSchemas.entrySet()) {
            // lib does not implement equals() for Regexp class, so...
            Optional<Regexp> newKey = newPatternSchemas.keySet().stream()
                    .filter(r -> r.toString().equals(pattern.getKey().toString())).findAny();
            if (newKey.isEmpty()) {
                throw new IncompatibleSchemaException("Pattern " + pattern.getKey().toString()
                        + " is no longer defined for new schema. Consumers could rely on the schema specification for matching properties.");
            }

            // directly compare, while we're here
            verifyCompatibleTo(pattern.getValue(), newPatternSchemas.get(newKey.get()),
                    fullPropName(prefix, "(" + pattern + ")"));
        }
    }

    private static void verifyCompatibleTo(ArraySchema oldSchema, ArraySchema newSchema, String prefix)
            throws IncompatibleSchemaException {
        if (oldSchema.needsUniqueItems() && !newSchema.needsUniqueItems()) {
            throw new IncompatibleSchemaException("Old schema guaranteed unique items for array at "
                    + propLocationName(prefix) + " while new schema removes this guarantee");
        }

        if (oldSchema.getMinItems() != null && minAllowedItems(newSchema) < oldSchema.getMinItems()) {
            throw new IncompatibleSchemaException("Old schema had a minimum of " + oldSchema.getMinItems()
                    + " items for array at " + propLocationName(prefix)
                    + ", while new schema does not guarantee this number of items in array");
        }

        if (oldSchema.getMaxItems() != null && maxAllowedItems(newSchema) > oldSchema.getMaxItems()) {
            throw new IncompatibleSchemaException("Old schema had a maximum of " + oldSchema.getMaxItems()
                    + " items for array at " + propLocationName(prefix) + ", while new schema allows more items.");
        }

        if (oldSchema.getAllItemSchema() != null) {
            if (newSchema.getAllItemSchema() == null) {
                if (newSchema.getContainedItemSchema() != null) {
                    throw new IncompatibleSchemaException("Array at " + propLocationName(prefix)
                            + " cannot be reduced from a schema for ALL items to a schema for at least ONE (contains) item");
                }
                if (newSchema.getItemSchemas() == null) {
                    throw new IncompatibleSchemaException("Array at " + propLocationName(prefix)
                            + " had a strong typing for items before which cannot be removed (old consumers could rely on that format)");
                }
                for (int i = 0; i < newSchema.getItemSchemas().size(); i++) {
                    verifyCompatibleTo(oldSchema.getAllItemSchema(), newSchema.getItemSchemas().get(i),
                            prefix + "[" + i + "]");
                }
            }
            else {
                verifyCompatibleTo(oldSchema.getAllItemSchema(), newSchema.getAllItemSchema(), prefix + "[all]");
            }
        }
        else if (oldSchema.getContainedItemSchema() != null) {
            if (newSchema.getContainedItemSchema() != null) {
                verifyCompatibleTo(oldSchema.getContainedItemSchema(), newSchema.getContainedItemSchema(),
                        prefix + "[contains]");
            }
            else if (newSchema.getAllItemSchema() != null) {
                verifyCompatibleTo(oldSchema.getContainedItemSchema(), newSchema.getAllItemSchema(),
                        prefix + "[contains/all]");
            }
            else if (newSchema.getItemSchemas() != null) {
                // fine if at least ONE item matches!
                boolean match = false;
                for (int i = 0; i < newSchema.getItemSchemas().size(); i++) {
                    try {
                        verifyCompatibleTo(oldSchema.getContainedItemSchema(), newSchema.getItemSchemas().get(i),
                                prefix + "[" + i + "]");
                        match = true;
                        break;
                    }
                    catch (IncompatibleSchemaException ex) {
                        // ignore here - no match
                    }
                }
                if (!match) {
                    throw new IncompatibleSchemaException("Array at " + propLocationName(prefix)
                            + " had a contains definition before which can not be found for any item of that array in the new schema");
                }
            }
        }
        else if (oldSchema.getItemSchemas() != null) {
            if (newSchema.getItemSchemas() != null) {
                // must either contain more elements (if oldSchema allows this), or exactly the
                // same
                if (newSchema.getItemSchemas().size() < oldSchema.getItemSchemas().size()) {
                    throw new IncompatibleSchemaException("Array at " + propLocationName(prefix)
                            + " had a strong typing for " + oldSchema.getItemSchemas().size()
                            + " elements, but new schema only defines " + newSchema.getItemSchemas().size() + " here");
                }
                if (newSchema.getItemSchemas().size() > oldSchema.getItemSchemas().size()
                        && (!oldSchema.permitsAdditionalItems() || (oldSchema.getMaxItems() != null
                                && oldSchema.getMaxItems() < newSchema.getItemSchemas().size()))) {
                    throw new IncompatibleSchemaException("New schema defines more items in array at "
                            + propLocationName(prefix) + " than were previously allowed here.");
                }
                for (int i = 0; i < oldSchema.getItemSchemas().size(); i++) {
                    verifyCompatibleTo(oldSchema.getItemSchemas().get(i), newSchema.getItemSchemas().get(i),
                            prefix + "[" + i + "]");
                }
            }
            else if (newSchema.getAllItemSchema() != null) {
                // first of all, ensure array size is compatible
                int oldSize = oldSchema.getItemSchemas().size();
                if (newSchema.getMinItems() == null || newSchema.getMinItems() < oldSize) {
                    throw new IncompatibleSchemaException(
                            "Old schema defined " + oldSize + " items for array at " + propLocationName(prefix)
                                    + ", but new schema does not guarantee this number of items in the array");
                }
                if (!oldSchema.permitsAdditionalItems()
                        && (newSchema.getMaxItems() == null || newSchema.getMaxItems() > oldSize)) {
                    throw new IncompatibleSchemaException("Old schema did not accept additional items for array at "
                            + propLocationName(prefix) + ", but new schema does not limit number of items in array");
                }

                // must match ALL previous items
                for (int i = 0; i < oldSchema.getItemSchemas().size(); i++) {
                    verifyCompatibleTo(oldSchema.getItemSchemas().get(i), newSchema.getAllItemSchema(),
                            prefix + "[" + i + "]");
                }
            }
            else {
                throw new IncompatibleSchemaException(
                        "Old schema made stronger guarantees for array at " + propLocationName(prefix)
                                + " than new schema does. Consumers could rely on previously guaranteed items");
            }
        }
    }

    private static final double DELTA = 0.0000001;

    private static void verifyCompatibleTo(NumberSchema oldSchema, NumberSchema newSchema, String prefix)
            throws IncompatibleSchemaException {
        if (oldSchema.getMultipleOf() != null) {
            if (newSchema.getMultipleOf() == null) {
                throw new IncompatibleSchemaException(
                        "multipleOf constraint must not be removed on number property " + propLocationName(prefix));
            }
            Number m1 = oldSchema.getMultipleOf();
            Number m2 = newSchema.getMultipleOf();

            // avoid dumb DIV/0
            if (Math.abs(m1.doubleValue()) > DELTA) {
                double testVal = m2.doubleValue() / m1.doubleValue();
                testVal -= Math.round(testVal);
                if (testVal > DELTA) {
                    throw new IncompatibleSchemaException("multipleOf constraint of property "
                            + propLocationName(prefix) + " changed in an incompatible way");
                }
            }
        }

        if (oldSchema.requiresInteger() && !newSchema.requiresInteger()) {
            throw new IncompatibleSchemaException("Old schema only allowed integer values at property "
                    + propLocationName(prefix)
                    + " while new schema allows non-integer values. Consumers may not be able to handle non-integer values");
        }

        if (allowsForLowerThan(oldSchema, newSchema)) {
            throw new IncompatibleSchemaException("New schema allows lower values for number property "
                    + propLocationName(prefix) + " than old schema. Consumers may not be prepared for this.");
        }
        if (allowsForGreaterThan(oldSchema, newSchema)) {
            throw new IncompatibleSchemaException("New schema allows greater values for number property "
                    + propLocationName(prefix) + " than old schema. Consumers may not be prepared for this.");
        }
    }

    private static void verifyCompatibleTo(StringSchema oldSchema, StringSchema newSchema, String prefix)
            throws IncompatibleSchemaException {
        if (oldSchema.getMinLength() != null
                && (newSchema.getMinLength() == null || newSchema.getMinLength() < oldSchema.getMinLength())) {
            throw new IncompatibleSchemaException(
                    "New schema allows for shorter strings than old schema for string property "
                            + propLocationName(prefix));
        }
        if (oldSchema.getMaxLength() != null
                && (newSchema.getMaxLength() == null || newSchema.getMaxLength() > oldSchema.getMaxLength())) {
            throw new IncompatibleSchemaException(
                    "New schema allows for longer strings than old schema for string property "
                            + propLocationName(prefix));
        }

        if (oldSchema.getPattern() != null && (newSchema.getPattern() == null
                || !newSchema.getPattern().toString().equals(oldSchema.getPattern().toString()))) {
            throw new IncompatibleSchemaException(
                    "New schema defines no or other pattern for string property " + propLocationName(prefix));
        }

        if (oldSchema.getFormatValidator() != null && (newSchema.getFormatValidator() == null
                || !oldSchema.getFormatValidator().formatName().equals(newSchema.getFormatValidator().formatName()))) {
            throw new IncompatibleSchemaException("format differs for string property " + propLocationName(prefix));
        }
    }

    private static void verifyCompatibleTo(CombinedSchema oldSchema, CombinedSchema newSchema, String prefix)
            throws IncompatibleSchemaException {
        if (oldSchema.getCriterion() != newSchema.getCriterion()) {
            throw new IncompatibleSchemaException(
                    "New schema defines differed combination operator at " + propLocationName(prefix));
        }

        // every new subschema must be compatible to a previous old schema
        int i = 0;
        for (Schema schema : newSchema.getSubschemas()) {
            boolean match = false;
            for (Schema os : oldSchema.getSubschemas()) {
                try {
                    verifyCompatibleTo(os, schema, prefix + "(" + newSchema.getCriterion() + ")[" + i + "]");
                    match = true;
                    break;
                }
                catch (IncompatibleSchemaException ex) {
                    // ignore; try next
                }
            }
            if (!match) {
                throw new IncompatibleSchemaException(
                        "New schema contains at least one incompatible subschema at property "
                                + propLocationName(prefix));
            }
            i++;
        }
    }

    private static void verifyCompatibleTo(NotSchema oldSchema, NotSchema newSchema, String prefix)
            throws IncompatibleSchemaException {
        // intentionally swapped parameters, because negated schema must get more greedy
        // to have the effect of "stricter" for the not-schema.
        verifyCompatibleTo(newSchema.getMustNotMatch(), oldSchema.getMustNotMatch(), prefix + "(not)");
    }

    private static void verifyCompatibleTo(ReferenceSchema oldSchema, ReferenceSchema newSchema, String prefix)
            throws IncompatibleSchemaException {
        if (oldSchema.getReferredSchema() != null) {
            if (newSchema.getReferredSchema() == null) {
                throw new IncompatibleSchemaException(
                        "Reference at " + propLocationName(prefix) + " could not be resolved");
            }

            verifyCompatibleTo(oldSchema.getReferredSchema(), newSchema.getReferredSchema(), prefix + oldSchema);
        }
    }

    private static void verifyCompatibleTo(ConstSchema oldSchema, ConstSchema newSchema, String prefix)
            throws IncompatibleSchemaException {
        if (oldSchema.getPermittedValue() == null ? newSchema.getPermittedValue() != null
                : !Objects.deepEquals(oldSchema.getPermittedValue(), newSchema.getPermittedValue())) {
            throw new IncompatibleSchemaException(
                    "Constant value for property " + propLocationName(prefix) + " has changed in new schema");
        }
    }

    private static void verifyCompatibleTo(ConditionalSchema oldSchema, ConditionalSchema newSchema, String prefix)
            throws IncompatibleSchemaException {
        if (oldSchema.getIfSchema().isPresent() != newSchema.getIfSchema().isPresent()
                || oldSchema.getThenSchema().isPresent() != newSchema.getThenSchema().isPresent()
                || oldSchema.getElseSchema().isPresent() != newSchema.getElseSchema().isPresent()) {
            throw new IncompatibleSchemaException("Conditional schema differs at " + propLocationName(prefix));
        }

        if (oldSchema.getIfSchema().isPresent()) {
            verifyCompatibleTo(oldSchema.getIfSchema().get(), newSchema.getIfSchema().orElseThrow(), prefix);
        }
        if (oldSchema.getThenSchema().isPresent()) {
            verifyCompatibleTo(oldSchema.getThenSchema().get(), newSchema.getThenSchema().orElseThrow(), prefix);
        }
        if (oldSchema.getElseSchema().isPresent()) {
            verifyCompatibleTo(oldSchema.getElseSchema().get(), newSchema.getElseSchema().orElseThrow(), prefix);
        }
    }

    private static void verifyCompatibleTo(EnumSchema oldSchema, EnumSchema newSchema, String prefix)
            throws IncompatibleSchemaException {
        // no new values must have been added
        for (Object o : newSchema.getPossibleValues()) {
            if (!oldSchema.getPossibleValues().contains(o)) {
                throw new IncompatibleSchemaException("Schema introduces new enum value for property "
                        + propLocationName(prefix) + ": " + o + ". Consumers may not expect this value.");
            }
        }
    }

    private static void verifyCombinedReplacedBySubschema(CombinedSchema oldSchema, Schema newSchema, String prefix)
            throws IncompatibleSchemaException {
        String crit = oldSchema.getCriterion().toString();
        if (!"oneOf".equals(crit) && !"anyOf".equals(crit)) {
            throw new IncompatibleSchemaException("Previously combined schema at " + propLocationName(prefix)
                    + " replaced by " + newSchema.getClass().getSimpleName());
        }

        for (Schema os : oldSchema.getSubschemas()) {
            try {
                verifyCompatibleTo(os, newSchema, prefix);
                return;
            }
            catch (IncompatibleSchemaException ex) {
                // next
            }
        }

        throw new IncompatibleSchemaException(
                "Previously combined schema at " + propLocationName(prefix) + " replaced by incompatible new schema");
    }

    private static String fullPropName(String prefix, String property) {
        return prefix == null ? property : (prefix + "." + property);
    }

    private static String propLocationName(String prefix) {
        return prefix == null ? "root" : prefix;
    }

    private static int minAllowedItems(ArraySchema schema) {
        if (schema.getMinItems() != null) {
            return schema.getMinItems();
        }
        if (schema.getItemSchemas() != null) {
            return schema.getItemSchemas().size();
        }
        return 0;
    }

    private static int maxAllowedItems(ArraySchema schema) {
        if (schema.getMaxItems() != null) {
            return schema.getMaxItems();
        }
        if (schema.getItemSchemas() != null && !schema.permitsAdditionalItems()) {
            return schema.getItemSchemas().size();
        }
        return Integer.MAX_VALUE;
    }

    private static boolean allowsForLowerThan(NumberSchema oldSchema, NumberSchema newSchema) {
        Number minOld = oldSchema.getMinimum();
        Number minOldExcl = oldSchema.isExclusiveMinimum() ? minOld : oldSchema.getExclusiveMinimumLimit();
        Number minNew = newSchema.getMinimum();
        Number minNewExcl = newSchema.isExclusiveMinimum() ? minNew : newSchema.getExclusiveMinimumLimit();

        if (minOld != null) {
            if (minNewExcl != null) {
                if (minNewExcl.doubleValue() >= minOld.doubleValue()) {
                    return false;
                }
                return !newSchema.requiresInteger() || minNewExcl.intValue() < minOld.intValue() - 1;
            }
            else {
                return minNew == null || minNew.doubleValue() < minOld.doubleValue() - DELTA;
            }
        }

        if (minOldExcl != null) {
            if (minNew != null) {
                if (minNew.doubleValue() < minOldExcl.doubleValue()) {
                    return true;
                }
                if (minNew.doubleValue() > minOldExcl.doubleValue() + 1) {
                    return false;
                }
                if (!newSchema.requiresInteger()) {
                    return true;
                }

                return minNew.intValue() != minOldExcl.intValue() + 1;
            }
            else {
                return minNewExcl == null || minNewExcl.doubleValue() < minOldExcl.doubleValue() - DELTA;
            }
        }

        // old schema had no min limit
        return false;
    }

    private static boolean allowsForGreaterThan(NumberSchema oldSchema, NumberSchema newSchema) {
        Number maxOld = oldSchema.getMaximum();
        Number maxOldExcl = oldSchema.isExclusiveMaximum() ? maxOld : oldSchema.getExclusiveMaximumLimit();
        Number maxNew = newSchema.getMaximum();
        Number maxNewExcl = newSchema.isExclusiveMaximum() ? maxNew : newSchema.getExclusiveMaximumLimit();

        if (maxOld != null) {
            if (maxNewExcl != null) {
                if (maxNewExcl.doubleValue() <= maxOld.doubleValue()) {
                    return false;
                }
                return !newSchema.requiresInteger() || maxNewExcl.intValue() > maxOld.intValue() + 1;
            }
            else {
                return maxNew == null || maxNew.doubleValue() > maxOld.doubleValue() + DELTA;
            }
        }

        if (maxOldExcl != null) {
            if (maxNew != null) {
                if (maxNew.doubleValue() > maxOldExcl.doubleValue()) {
                    return true;
                }
                if (maxNew.doubleValue() < maxOldExcl.doubleValue() - 1) {
                    return false;
                }
                if (!newSchema.requiresInteger()) {
                    return true;
                }

                return maxNew.intValue() != maxOldExcl.intValue() - 1;
            }
            else {
                return maxNewExcl == null || maxNewExcl.doubleValue() > maxOldExcl.doubleValue() + DELTA;
            }
        }

        // old schema had no max limit
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<Regexp, Schema> getPatternProperties(ObjectSchema schema) {
        try {
            Field field = ObjectSchema.class.getDeclaredField("patternProperties");
            field.setAccessible(true);
            Map<Regexp, Schema> result = (Map<Regexp, Schema>) field.get(schema);
            return result == null ? Collections.emptyMap() : result;
        }
        catch (SecurityException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
