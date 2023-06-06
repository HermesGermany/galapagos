package com.hermesworld.ais.galapagos.schemas;

import org.everit.json.schema.ArraySchema;

import static com.hermesworld.ais.galapagos.schemas.SchemaUtil.fullPropName;
import static com.hermesworld.ais.galapagos.schemas.SchemaUtil.propLocationName;

/**
 * As the compatibility validation is always "consumer-view", checks for producer compatibility are performed with
 * swapped schemas. This is why this error handler "swaps" the error messages again to make them more helpful. <br>
 * Also, it allows for some "greedy" settings which are useful in most cases.
 */
public class ProducerCompatibilityErrorHandler extends ConsumerCompatibilityErrorHandler {

    private final boolean allowNewOptionalProperties;

    public ProducerCompatibilityErrorHandler(boolean allowNewOptionalProperties) {
        this.allowNewOptionalProperties = allowNewOptionalProperties;
    }

    @Override
    public void handleDefinedPropertyNoLongerDefined(SchemaCompatibilityValidationContext context, String property)
            throws IncompatibleSchemaException {
        if (!allowNewOptionalProperties) {
            throw new IncompatibleSchemaException("Property " + fullPropName(context, property)
                    + " is newly defined. Producers could already use this property (as additional properties are allowed) in a different format.");
        }
    }

    @Override
    public void handleCombinedSchemaReplacedByIncompatibleSchema(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "Schemas at " + propLocationName(context) + " have changed in an incompatible way");
    }

    @Override
    public void handleSchemaTypeDiffers(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Schema type differs at " + propLocationName(context) + " (old: "
                + context.getCurrentNodeInNewSchema().getClass().getSimpleName() + ", new: "
                + context.getCurrentNodeInOldSchema().getClass().getSimpleName() + ")");
    }

    @Override
    public void handleAdditionalPropertiesIntroduced(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("additionalProperties must not be removed "
                + "in a newer schema version (old producers could currently provide additional properties)");
    }

    @Override
    public void handlePropertyNoLongerRequired(SchemaCompatibilityValidationContext context, String property)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Property " + fullPropName(context, property)
                + " is now required, but old producers may not yet provide it.");
    }

    @Override
    public void handleMinPropertiesReduced(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Minimum number of properties cannot be increased at "
                + propLocationName(context) + ". Old producers could provide too few properties.");
    }

    @Override
    public void handleRegexpPatternRemoved(SchemaCompatibilityValidationContext context, String pattern)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("New pattern " + pattern + " introduced in new schema at "
                + propLocationName(context) + ". Producers may not yet adhere to this pattern.");
    }

    @Override
    public void handleMultipleOfConstraintRemoved(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("multipleOf constraint added on number property "
                + propLocationName(context) + ". Current producers may provide incompatible values.");
    }

    @Override
    public void handleIntegerChangedToNumber(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "Old schema allowed any numeric values at property " + propLocationName(context)
                        + " while new schema only allows integer values. Producers may provide incompatible values.");
    }

    @Override
    public void handleNumberAllowsLowerValues(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("New schema has increased lower boundary for values for number property "
                + propLocationName(context) + " than old schema. Producers may provide incompatible values.");
    }

    @Override
    public void handleNumberAllowsGreaterValues(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("New schema has decreased upper boundary for values for number property "
                + propLocationName(context) + " than old schema. Providers may provide incompatible values.");
    }

    @Override
    public void handleArrayUniqueItemsRemoved(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("New schema requires unique items for array at "
                + propLocationName(context) + ". Producers may not fulfill this requirement.");
    }

    @Override
    public void handleArrayAllowsLessItems(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "New schema requires a minimum of " + ((ArraySchema) context.getCurrentNodeInOldSchema()).getMinItems()
                        + " items for array at " + propLocationName(context)
                        + ", while old schema did not require that many items. Producers may provide too few items.");
    }

    @Override
    public void handleArrayAllowsMoreItems(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "New schema allows a maximum of " + ((ArraySchema) context.getCurrentNodeInOldSchema()).getMaxItems()
                        + " items for array at " + propLocationName(context)
                        + ", while old schema allowed more items. Producers may provide too many items.");
    }

    @Override
    public void handleArrayAllSchemaToContainsSchema(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Array at " + propLocationName(context)
                + " cannot be changed from a schema for at least ONE (contains) item to a schema for ALL items.");
    }

    @Override
    public void handleArrayItemSchemaRemoved(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "Array at " + propLocationName(context) + " cannot introduce a strong typing for items.");
    }

    @Override
    public void handleArrayContainsSchemaNotFound(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Array at " + propLocationName(context)
                + " introduces a contains definition which was previously not present as any schema.");
    }

    @Override
    public void handleArrayLessItemSchemas(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "Array at " + propLocationName(context) + " defines strong schemas for more items than before.");
    }

    @Override
    public void handleArrayMoreItemSchemasThanAllowed(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("New schema allows less items in array at " + propLocationName(context)
                + " than were previously allowed here.");
    }

    @Override
    public void handleArrayLessItemsThanSchemas(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        ArraySchema newSchema = (ArraySchema) context.getCurrentNodeInOldSchema();
        throw new IncompatibleSchemaException(
                "New schema defines " + newSchema.getItemSchemas().size() + " items for array at "
                        + propLocationName(context) + ", but old schema did not force this number of items.");
    }

    @Override
    public void handleArrayMoreItemsThanSchemas(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("New schema does not accept additional items for array at "
                + propLocationName(context)
                + ", but old schema had higher limit for number of items in array. Producers may provide too many items.");
    }

    @Override
    public void handleArrayItemSchemasRemoved(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "New schema makes stronger requirements for array at " + propLocationName(context)
                        + " than old schema does. Producers may not adhere to stronger definition.");
    }

    @Override
    public void handleShorterStringsAllowed(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "New schema requires longer strings than old schema for string property " + propLocationName(context));
    }

    @Override
    public void handleLongerStringsAllowed(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "New schema requires shorter strings than old schema for string property " + propLocationName(context));
    }

    @Override
    public void handleEnumValueAdded(SchemaCompatibilityValidationContext context, String value)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Schema removes enum value for property " + propLocationName(context)
                + ": " + value + ". Current producers may still use this value.");
    }

}
