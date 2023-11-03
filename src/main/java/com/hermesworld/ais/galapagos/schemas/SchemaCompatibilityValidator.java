package com.hermesworld.ais.galapagos.schemas;

import org.everit.json.schema.*;
import org.everit.json.schema.regexp.Regexp;

import java.lang.reflect.Field;
import java.util.*;

public class SchemaCompatibilityValidator {

    // Known limitations:

    // - Regex Pattern compatibility is not checked - every difference is treated as incompatible.
    // Theoretically, there could be stricter patterns which include the previous patterns.

    private final ValidationContextImpl context = new ValidationContextImpl();

    private final SchemaCompatibilityErrorHandler errorHandler;

    public SchemaCompatibilityValidator(Schema oldSchema, Schema newSchema,
            SchemaCompatibilityErrorHandler errorHandler) {
        context.oldSchema = oldSchema;
        context.newSchema = newSchema;
        this.errorHandler = errorHandler;
    }

    public void validate() throws IncompatibleSchemaException {
        context.prefixSegments.clear();
        verifySchemasCompatible(context.oldSchema, context.newSchema);
    }

    private void verifySchemasCompatible(Schema oldSchema, Schema newSchema) throws IncompatibleSchemaException {
        setCurrentNodes(oldSchema, newSchema);

        if (oldSchema.getClass() == EmptySchema.class) {
            // always compatible
            return;
        }

        // special case: "anyOf" or "oneOf" can be replaced by one of its subschemas
        if (oldSchema.getClass() == CombinedSchema.class && newSchema.getClass() != CombinedSchema.class) {
            verifyCombinedReplacedBySubschema((CombinedSchema) oldSchema, newSchema);
            return;
        }

        if (newSchema.getClass() != oldSchema.getClass()) {
            errorHandler.handleSchemaTypeDiffers(context);
            return;
        }

        if (newSchema.getClass() == ObjectSchema.class) {
            verifySchemasCompatible((ObjectSchema) oldSchema, (ObjectSchema) newSchema);
        }
        else if (newSchema.getClass() == StringSchema.class) {
            verifySchemasCompatible((StringSchema) oldSchema, (StringSchema) newSchema);
        }
        else if (newSchema.getClass() == EnumSchema.class) {
            verifySchemasCompatible((EnumSchema) oldSchema, (EnumSchema) newSchema);
        }
        else if (newSchema.getClass() == ArraySchema.class) {
            verifySchemasCompatible((ArraySchema) oldSchema, (ArraySchema) newSchema);
        }
        else if (newSchema.getClass() == NumberSchema.class) {
            verifySchemasCompatible((NumberSchema) oldSchema, (NumberSchema) newSchema);
        }
        else if (newSchema.getClass() == CombinedSchema.class) {
            verifySchemasCompatible((CombinedSchema) oldSchema, (CombinedSchema) newSchema);
        }
        else if (newSchema.getClass() == ConditionalSchema.class) {
            verifySchemasCompatible((ConditionalSchema) oldSchema, (ConditionalSchema) newSchema);
        }
        else if (newSchema.getClass() == NotSchema.class) {
            verifySchemasCompatible((NotSchema) oldSchema, (NotSchema) newSchema);
        }
        else if (newSchema.getClass() == ConstSchema.class) {
            verifySchemasCompatible((ConstSchema) oldSchema, (ConstSchema) newSchema);
        }
        else if (newSchema.getClass() == ReferenceSchema.class) {
            verifySchemasCompatible((ReferenceSchema) oldSchema, (ReferenceSchema) newSchema);
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

    private void verifySchemasCompatible(ObjectSchema oldSchema, ObjectSchema newSchema)
            throws IncompatibleSchemaException {
        setCurrentNodes(oldSchema, newSchema);

        if (!oldSchema.permitsAdditionalProperties() && newSchema.permitsAdditionalProperties()) {
            errorHandler.handleAdditionalPropertiesIntroduced(context);
            return;
        }

        // required properties must still be required
        for (String property : oldSchema.getRequiredProperties()) {
            if (!newSchema.getRequiredProperties().contains(property)) {
                errorHandler.handlePropertyNoLongerRequired(context, property);
                return;
            }
        }

        // min properties cannot be reduced (consumer could rely on having at least N
        // properties)
        if (oldSchema.getMinProperties() != null && (newSchema.getMinProperties() == null
                || newSchema.getMinProperties() < oldSchema.getMinProperties())) {
            errorHandler.handleMinPropertiesReduced(context);
            return;
        }

        // Previously strongly typed (optional) properties must still be available with
        // same format
        oldPropsLoop: for (String property : oldSchema.getPropertySchemas().keySet()) {
            Schema oldPropSchema = oldSchema.getPropertySchemas().get(property);

            // simple case: still exists directly
            Schema newPropSchema = newSchema.getPropertySchemas().get(property);
            if (newPropSchema != null) {
                pushPrefix(".", property);
                verifySchemasCompatible(oldPropSchema, newPropSchema);
                popPrefix();
                continue;
            }

            // could now be inside the dependencies (verify ALL occurrences to be compatible
            // to previous schema)
            boolean found = false;
            for (Schema newDepSchema : newSchema.getSchemaDependencies().values()) {
                if (!(newDepSchema instanceof ObjectSchema)) {
                    errorHandler.handleInvalidDependenciesConfiguration(context);
                    return;
                }
                newPropSchema = ((ObjectSchema) newDepSchema).getPropertySchemas().get(property);
                if (newPropSchema != null) {
                    pushPrefix(".", property);
                    verifySchemasCompatible(oldPropSchema, newPropSchema);
                    popPrefix();
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
                    pushPrefix(".", property);
                    verifySchemasCompatible(oldPropSchema, entry.getValue());
                    popPrefix();
                    // JSON Schema logic: First matching pattern wins...
                    continue oldPropsLoop;
                }
            }

            // if no additionalProperties allowed in new schema, we are fine, because will not occur in different format
            if (!newSchema.permitsAdditionalProperties() && !oldSchema.getRequiredProperties().contains(property)) {
                continue;
            }

            // OK, defined nowhere, so we can't be sure that it will still be generated in same form
            errorHandler.handleDefinedPropertyNoLongerDefined(context, property);
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
                errorHandler.handleRegexpPatternRemoved(context, pattern.getKey().toString());
                return;
            }

            // directly compare, while we're here
            pushPrefix("", "(" + pattern + ")");
            verifySchemasCompatible(pattern.getValue(), newPatternSchemas.get(newKey.get()));
            popPrefix();
        }
    }

    private void verifySchemasCompatible(ArraySchema oldSchema, ArraySchema newSchema)
            throws IncompatibleSchemaException {
        setCurrentNodes(oldSchema, newSchema);

        if (oldSchema.needsUniqueItems() && !newSchema.needsUniqueItems()) {
            errorHandler.handleArrayUniqueItemsRemoved(context);
        }

        if (oldSchema.getMinItems() != null && minAllowedItems(newSchema) < oldSchema.getMinItems()) {
            errorHandler.handleArrayAllowsLessItems(context);
        }

        if (oldSchema.getMaxItems() != null && maxAllowedItems(newSchema) > oldSchema.getMaxItems()) {
            errorHandler.handleArrayAllowsMoreItems(context);
        }

        if (oldSchema.getAllItemSchema() != null) {
            if (newSchema.getAllItemSchema() == null) {
                if (newSchema.getContainedItemSchema() != null) {
                    errorHandler.handleArrayAllSchemaToContainsSchema(context);
                }
                else if (newSchema.getItemSchemas() == null) {
                    errorHandler.handleArrayItemSchemaRemoved(context);
                }
                else {
                    for (int i = 0; i < newSchema.getItemSchemas().size(); i++) {
                        pushPrefix("[" + i + "]");
                        verifySchemasCompatible(oldSchema.getAllItemSchema(), newSchema.getItemSchemas().get(i));
                        popPrefix();
                    }
                }
            }
            else {
                pushPrefix("[all]");
                verifySchemasCompatible(oldSchema.getAllItemSchema(), newSchema.getAllItemSchema());
                popPrefix();
            }
        }
        else if (oldSchema.getContainedItemSchema() != null) {
            if (newSchema.getContainedItemSchema() != null) {
                pushPrefix("[contains]");
                verifySchemasCompatible(oldSchema.getContainedItemSchema(), newSchema.getContainedItemSchema());
                popPrefix();
            }
            else if (newSchema.getAllItemSchema() != null) {
                pushPrefix("[contains/all]");
                verifySchemasCompatible(oldSchema.getContainedItemSchema(), newSchema.getAllItemSchema());
                popPrefix();
            }
            else if (newSchema.getItemSchemas() != null) {
                // fine if at least ONE item matches!
                boolean match = false;
                for (int i = 0; i < newSchema.getItemSchemas().size(); i++) {
                    pushPrefix("[" + i + "]");
                    try {
                        verifySchemasCompatible(oldSchema.getContainedItemSchema(), newSchema.getItemSchemas().get(i));
                        match = true;
                        break;
                    }
                    catch (IncompatibleSchemaException ex) {
                        // ignore here - no match
                    }
                    finally {
                        popPrefix();
                    }
                }
                if (!match) {
                    errorHandler.handleArrayContainsSchemaNotFound(context);
                }
            }
        }
        else if (oldSchema.getItemSchemas() != null) {
            if (newSchema.getItemSchemas() != null) {
                // must either contain more elements (if oldSchema allows this), or exactly the same
                if (newSchema.getItemSchemas().size() < oldSchema.getItemSchemas().size()) {
                    errorHandler.handleArrayLessItemSchemas(context);
                }
                else if (newSchema.getItemSchemas().size() > oldSchema.getItemSchemas().size()
                        && (!oldSchema.permitsAdditionalItems() || (oldSchema.getMaxItems() != null
                                && oldSchema.getMaxItems() < newSchema.getItemSchemas().size()))) {
                    errorHandler.handleArrayMoreItemSchemasThanAllowed(context);
                }
                else {
                    for (int i = 0; i < oldSchema.getItemSchemas().size(); i++) {
                        pushPrefix("[" + i + "]");
                        verifySchemasCompatible(oldSchema.getItemSchemas().get(i), newSchema.getItemSchemas().get(i));
                        popPrefix();
                    }
                }
            }
            else if (newSchema.getAllItemSchema() != null) {
                // first of all, ensure array size is compatible
                int oldSize = oldSchema.getItemSchemas().size();
                if (newSchema.getMinItems() == null || newSchema.getMinItems() < oldSize) {
                    errorHandler.handleArrayLessItemsThanSchemas(context);
                }
                else if (!oldSchema.permitsAdditionalItems()
                        && (newSchema.getMaxItems() == null || newSchema.getMaxItems() > oldSize)) {
                    errorHandler.handleArrayMoreItemsThanSchemas(context);
                }
                else {
                    // must match ALL previous items
                    for (int i = 0; i < oldSchema.getItemSchemas().size(); i++) {
                        pushPrefix("[" + i + "]");
                        verifySchemasCompatible(oldSchema.getItemSchemas().get(i), newSchema.getAllItemSchema());
                        popPrefix();
                    }
                }
            }
            else {
                errorHandler.handleArrayItemSchemasRemoved(context);
            }
        }
    }

    private void verifySchemasCompatible(CombinedSchema oldSchema, CombinedSchema newSchema)
            throws IncompatibleSchemaException {
        setCurrentNodes(oldSchema, newSchema);
        if (oldSchema.getCriterion() != newSchema.getCriterion()) {
            errorHandler.handleCombineOperatorChanged(context);
            return;
        }

        // every new subschema must be compatible to a previous old schema
        int i = 0;
        for (Schema schema : newSchema.getSubschemas()) {
            boolean match = false;
            for (Schema os : oldSchema.getSubschemas()) {
                pushPrefix("(" + newSchema.getCriterion() + ")[" + i + "]");
                try {
                    verifySchemasCompatible(os, schema);
                    match = true;
                    break;
                }
                catch (IncompatibleSchemaException ex) {
                    // ignore; try next
                }
                finally {
                    popPrefix();
                }
            }
            if (!match) {
                errorHandler.handleCombineSubschemaNoMatch(context);
            }
            i++;
        }
    }

    private void verifySchemasCompatible(StringSchema oldSchema, StringSchema newSchema)
            throws IncompatibleSchemaException {
        setCurrentNodes(oldSchema, newSchema);

        if (oldSchema.getMinLength() != null
                && (newSchema.getMinLength() == null || newSchema.getMinLength() < oldSchema.getMinLength())) {
            errorHandler.handleShorterStringsAllowed(context);
        }
        if (oldSchema.getMaxLength() != null
                && (newSchema.getMaxLength() == null || newSchema.getMaxLength() > oldSchema.getMaxLength())) {
            errorHandler.handleLongerStringsAllowed(context);
        }

        if (oldSchema.getPattern() != null && (newSchema.getPattern() == null
                || !newSchema.getPattern().toString().equals(oldSchema.getPattern().toString()))) {
            errorHandler.handleStringPatternChanged(context);
        }

        if (oldSchema.getFormatValidator() != null && (newSchema.getFormatValidator() == null
                || !oldSchema.getFormatValidator().formatName().equals(newSchema.getFormatValidator().formatName()))) {
            errorHandler.handleStringFormatChanged(context);
        }
    }

    private static final double DELTA = 0.0000001;

    private void verifySchemasCompatible(NumberSchema oldSchema, NumberSchema newSchema)
            throws IncompatibleSchemaException {
        setCurrentNodes(oldSchema, newSchema);
        if (oldSchema.getMultipleOf() != null) {
            if (newSchema.getMultipleOf() == null) {
                errorHandler.handleMultipleOfConstraintRemoved(context);
            }
            else {
                Number m1 = oldSchema.getMultipleOf();
                Number m2 = newSchema.getMultipleOf();

                // avoid dumb DIV/0
                if (Math.abs(m1.doubleValue()) > DELTA) {
                    double testVal = m2.doubleValue() / m1.doubleValue();
                    testVal -= Math.round(testVal);
                    if (testVal > DELTA) {
                        errorHandler.handleMultipleOfConstraintChanged(context);
                    }
                }
            }
        }

        if (oldSchema.requiresInteger() && !newSchema.requiresInteger()) {
            errorHandler.handleIntegerChangedToNumber(context);
        }

        if (allowsForLowerThan(oldSchema, newSchema)) {
            errorHandler.handleNumberAllowsLowerValues(context);
        }
        if (allowsForGreaterThan(oldSchema, newSchema)) {
            errorHandler.handleNumberAllowsGreaterValues(context);
        }
    }

    private void verifySchemasCompatible(NotSchema oldSchema, NotSchema newSchema) throws IncompatibleSchemaException {
        setCurrentNodes(oldSchema, newSchema);

        // intentionally swapped parameters, because negated schema must get more liberal
        // to have the effect of "stricter" for the not-schema.
        pushPrefix("(not)");
        verifySchemasCompatible(newSchema.getMustNotMatch(), oldSchema.getMustNotMatch());
        popPrefix();
    }

    private void verifySchemasCompatible(ReferenceSchema oldSchema, ReferenceSchema newSchema)
            throws IncompatibleSchemaException {
        setCurrentNodes(oldSchema, newSchema);

        if (oldSchema.getReferredSchema() != null) {
            if (newSchema.getReferredSchema() == null) {
                errorHandler.handleUnresolvedSchemaReference(context);
            }
            else {
                pushPrefix(oldSchema.getReferenceValue());
                verifySchemasCompatible(oldSchema.getReferredSchema(), newSchema.getReferredSchema());
                popPrefix();
            }
        }
    }

    private void verifySchemasCompatible(ConstSchema oldSchema, ConstSchema newSchema)
            throws IncompatibleSchemaException {
        setCurrentNodes(oldSchema, newSchema);
        if (oldSchema.getPermittedValue() == null ? newSchema.getPermittedValue() != null
                : !Objects.deepEquals(oldSchema.getPermittedValue(), newSchema.getPermittedValue())) {
            errorHandler.handleConstantValueChanged(context);
        }
    }

    private void verifySchemasCompatible(ConditionalSchema oldSchema, ConditionalSchema newSchema)
            throws IncompatibleSchemaException {
        setCurrentNodes(oldSchema, newSchema);
        if (oldSchema.getIfSchema().isPresent() != newSchema.getIfSchema().isPresent()
                || oldSchema.getThenSchema().isPresent() != newSchema.getThenSchema().isPresent()
                || oldSchema.getElseSchema().isPresent() != newSchema.getElseSchema().isPresent()) {
            errorHandler.handleConditionalSchemaDiffers(context);
        }

        if (oldSchema.getIfSchema().isPresent()) {
            verifySchemasCompatible(oldSchema.getIfSchema().get(), newSchema.getIfSchema().orElseThrow());
        }
        if (oldSchema.getThenSchema().isPresent()) {
            verifySchemasCompatible(oldSchema.getThenSchema().get(), newSchema.getThenSchema().orElseThrow());
        }
        if (oldSchema.getElseSchema().isPresent()) {
            verifySchemasCompatible(oldSchema.getElseSchema().get(), newSchema.getElseSchema().orElseThrow());
        }
    }

    private void verifySchemasCompatible(EnumSchema oldSchema, EnumSchema newSchema)
            throws IncompatibleSchemaException {
        setCurrentNodes(oldSchema, newSchema);

        // no new values must have been added
        for (Object o : newSchema.getPossibleValues()) {
            if (!oldSchema.getPossibleValues().contains(o)) {
                errorHandler.handleEnumValueAdded(context, o.toString());
                return;
            }
        }
    }

    private void verifyCombinedReplacedBySubschema(CombinedSchema oldSchema, Schema newSchema)
            throws IncompatibleSchemaException {
        setCurrentNodes(oldSchema, newSchema);

        String crit = oldSchema.getCriterion().toString();
        if (!"oneOf".equals(crit) && !"anyOf".equals(crit)) {
            errorHandler.handleCombinedSchemaReplacedByIncompatibleSchema(context);
            // if that is fine for error handler, no need to check further
            return;
        }

        for (Schema os : oldSchema.getSubschemas()) {
            try {
                verifySchemasCompatible(os, newSchema);
                return;
            }
            catch (IncompatibleSchemaException ex) {
                // next
            }
        }

        errorHandler.handleCombinedSchemaReplacedByIncompatibleSchema(context);
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

    private void pushPrefix(String segment) {
        pushPrefix("", segment);
    }

    private void pushPrefix(String separator, String segment) {
        context.prefixSegments.push(context.prefixSegments.isEmpty() ? segment : separator + segment);
    }

    private void popPrefix() {
        context.prefixSegments.pop();
    }

    private void setCurrentNodes(Schema currentOldNode, Schema currentNewNode) {
        context.currentOldNode = currentOldNode;
        context.currentNewNode = currentNewNode;
    }

    private static class ValidationContextImpl implements SchemaCompatibilityValidationContext {

        private Schema oldSchema;

        private Schema newSchema;

        private final Stack<String> prefixSegments = new Stack<>();

        private Schema currentOldNode;

        private Schema currentNewNode;

        @Override
        public String getCurrentPrefix() {
            return prefixSegments.isEmpty() ? null : String.join("", prefixSegments);
        }

        @Override
        public Schema getOldSchema() {
            return oldSchema;
        }

        @Override
        public Schema getNewSchema() {
            return newSchema;
        }

        @Override
        public Schema getCurrentNodeInOldSchema() {
            return currentOldNode;
        }

        @Override
        public Schema getCurrentNodeInNewSchema() {
            return currentNewNode;
        }
    }
}
