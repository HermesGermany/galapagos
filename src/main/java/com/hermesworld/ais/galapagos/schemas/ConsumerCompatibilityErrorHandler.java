package com.hermesworld.ais.galapagos.schemas;

import org.everit.json.schema.ArraySchema;

import static com.hermesworld.ais.galapagos.schemas.SchemaUtil.fullPropName;
import static com.hermesworld.ais.galapagos.schemas.SchemaUtil.propLocationName;

public class ConsumerCompatibilityErrorHandler implements SchemaCompatibilityErrorHandler {

    private final boolean allowRemovedOptionalProperties;

    public ConsumerCompatibilityErrorHandler() {
        this(false);
    }

    public ConsumerCompatibilityErrorHandler(boolean allowRemovedOptionalProperties) {
        this.allowRemovedOptionalProperties = allowRemovedOptionalProperties;
    }

    @Override
    public void handleCombinedSchemaReplacedByIncompatibleSchema(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "Combined schema at " + propLocationName(context) + " is replaced by incompatible schema");
    }

    @Override
    public void handleSchemaTypeDiffers(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Schema type differs at " + propLocationName(context) + " (new: "
                + context.getCurrentNodeInNewSchema().getClass().getSimpleName() + ", old: "
                + context.getCurrentNodeInOldSchema().getClass().getSimpleName() + ")");
    }

    @Override
    public void handleAdditionalPropertiesIntroduced(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("additionalProperties must not be introduced "
                + "in a newer schema version (old consumers could rely on no additional properties being present)");
    }

    @Override
    public void handlePropertyNoLongerRequired(SchemaCompatibilityValidationContext context, String property)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Property " + fullPropName(context, property)
                + " is no longer required, but old consumers could rely on it.");
    }

    @Override
    public void handleMinPropertiesReduced(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Minimum number of properties cannot be reduced at "
                + propLocationName(context) + ". Old consumers could rely on having at least N properties.");
    }

    @Override
    public void handleDefinedPropertyNoLongerDefined(SchemaCompatibilityValidationContext context, String property)
            throws IncompatibleSchemaException {
        if (!allowRemovedOptionalProperties) {
            throw new IncompatibleSchemaException("Property " + fullPropName(context, property)
                    + " is no longer defined in schema, but had a strong definition before. "
                    + "Field format could have changed, and old consumers perhaps rely on previous format");
        }
    }

    @Override
    public void handleRegexpPatternRemoved(SchemaCompatibilityValidationContext context, String pattern)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "Pattern " + pattern + " is no longer defined for new schema at " + propLocationName(context)
                        + ". Consumers could rely on the schema specification for matching properties.");
    }

    @Override
    public void handleMultipleOfConstraintRemoved(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "multipleOf constraint must not be removed on number property " + propLocationName(context));
    }

    @Override
    public void handleMultipleOfConstraintChanged(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "multipleOf constraint of property " + propLocationName(context) + " changed in an incompatible way");
    }

    @Override
    public void handleIntegerChangedToNumber(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Old schema only allowed integer values at property "
                + propLocationName(context)
                + " while new schema allows non-integer values. Consumers may not be able to handle non-integer values");
    }

    @Override
    public void handleNumberAllowsLowerValues(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("New schema allows lower values for number property "
                + propLocationName(context) + " than old schema. Consumers may not be prepared for this.");
    }

    @Override
    public void handleNumberAllowsGreaterValues(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("New schema allows greater values for number property "
                + propLocationName(context) + " than old schema. Consumers may not be prepared for this.");
    }

    @Override
    public void handleArrayUniqueItemsRemoved(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Old schema guaranteed unique items for array at "
                + propLocationName(context) + " while new schema removes this guarantee");
    }

    @Override
    public void handleArrayAllowsLessItems(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Old schema had a minimum of "
                + ((ArraySchema) context.getCurrentNodeInOldSchema()).getMinItems() + " items for array at "
                + propLocationName(context) + ", while new schema does not guarantee this number of items in array");
    }

    @Override
    public void handleArrayAllowsMoreItems(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "Old schema had a maximum of " + ((ArraySchema) context.getCurrentNodeInOldSchema()).getMaxItems()
                        + " items for array at " + propLocationName(context) + ", while new schema allows more items.");
    }

    @Override
    public void handleArrayAllSchemaToContainsSchema(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Array at " + propLocationName(context)
                + " cannot be reduced from a schema for ALL items to a schema for at least ONE (contains) item");
    }

    @Override
    public void handleArrayItemSchemaRemoved(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Array at " + propLocationName(context)
                + " had a strong typing for items before which cannot be removed (old consumers could rely on that format)");
    }

    @Override
    public void handleArrayContainsSchemaNotFound(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Array at " + propLocationName(context)
                + " had a contains definition before which can not be found for any item of that array in the new schema");
    }

    @Override
    public void handleArrayLessItemSchemas(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        ArraySchema oldSchema = (ArraySchema) context.getCurrentNodeInOldSchema();
        ArraySchema newSchema = (ArraySchema) context.getCurrentNodeInNewSchema();
        throw new IncompatibleSchemaException("Array at " + propLocationName(context) + " had a strong typing for "
                + oldSchema.getItemSchemas().size() + " elements, but new schema only defines "
                + newSchema.getItemSchemas().size() + " here");
    }

    @Override
    public void handleArrayMoreItemSchemasThanAllowed(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("New schema defines more items in array at " + propLocationName(context)
                + " than were previously allowed here.");
    }

    @Override
    public void handleArrayLessItemsThanSchemas(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        ArraySchema oldSchema = (ArraySchema) context.getCurrentNodeInOldSchema();
        throw new IncompatibleSchemaException("Old schema defined " + oldSchema.getItemSchemas().size()
                + " items for array at " + propLocationName(context)
                + ", but new schema does not guarantee this number of items in the array");
    }

    @Override
    public void handleArrayMoreItemsThanSchemas(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Old schema did not accept additional items for array at "
                + propLocationName(context) + ", but new schema has higher limit for number of items in array");
    }

    @Override
    public void handleArrayItemSchemasRemoved(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "Old schema made stronger guarantees for array at " + propLocationName(context)
                        + " than new schema does. Consumers could rely on previously guaranteed items");
    }

    @Override
    public void handleShorterStringsAllowed(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "New schema allows for shorter strings than old schema for string property "
                        + propLocationName(context));
    }

    @Override
    public void handleLongerStringsAllowed(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "New schema allows for longer strings than old schema for string property "
                        + propLocationName(context));
    }

    @Override
    public void handleStringPatternChanged(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "New schema defines no or different pattern for string property " + propLocationName(context));
    }

    @Override
    public void handleStringFormatChanged(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("format differs for string property " + propLocationName(context));
    }

    @Override
    public void handleCombineOperatorChanged(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "New schema defines differed combination operator at " + propLocationName(context));
    }

    @Override
    public void handleCombineSubschemaNoMatch(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "New schema contains at least one incompatible subschema at property " + propLocationName(context));
    }

    @Override
    public void handleConstantValueChanged(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "Constant value for property " + propLocationName(context) + " has changed in new schema");
    }

    @Override
    public void handleConditionalSchemaDiffers(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Conditional schema differs at " + propLocationName(context));
    }

    @Override
    public void handleEnumValueAdded(SchemaCompatibilityValidationContext context, String value)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Schema introduces new enum value for property "
                + propLocationName(context) + ": " + value + ". Consumers may not expect this value.");
    }

}
