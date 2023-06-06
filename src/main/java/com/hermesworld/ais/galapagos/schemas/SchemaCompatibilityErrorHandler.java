package com.hermesworld.ais.galapagos.schemas;

import static com.hermesworld.ais.galapagos.schemas.SchemaUtil.propLocationName;

public interface SchemaCompatibilityErrorHandler {

    void handleCombinedSchemaReplacedByIncompatibleSchema(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException;

    void handleSchemaTypeDiffers(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    void handleAdditionalPropertiesIntroduced(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException;

    void handlePropertyNoLongerRequired(SchemaCompatibilityValidationContext context, String property)
            throws IncompatibleSchemaException;

    void handleMinPropertiesReduced(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    default void handleInvalidDependenciesConfiguration(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException(
                "Invalid dependencies configuration found at " + propLocationName(context));
    }

    void handleDefinedPropertyNoLongerDefined(SchemaCompatibilityValidationContext context, String property)
            throws IncompatibleSchemaException;

    void handleRegexpPatternRemoved(SchemaCompatibilityValidationContext context, String pattern)
            throws IncompatibleSchemaException;

    void handleMultipleOfConstraintRemoved(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException;

    void handleMultipleOfConstraintChanged(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException;

    void handleIntegerChangedToNumber(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    void handleNumberAllowsLowerValues(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    void handleNumberAllowsGreaterValues(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException;

    void handleArrayUniqueItemsRemoved(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    void handleArrayAllowsLessItems(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    void handleArrayAllowsMoreItems(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    void handleArrayAllSchemaToContainsSchema(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException;

    void handleArrayItemSchemaRemoved(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    void handleArrayContainsSchemaNotFound(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException;

    void handleArrayLessItemSchemas(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    void handleArrayMoreItemSchemasThanAllowed(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException;

    void handleArrayLessItemsThanSchemas(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException;

    void handleArrayMoreItemsThanSchemas(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException;

    void handleArrayItemSchemasRemoved(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    void handleShorterStringsAllowed(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    void handleLongerStringsAllowed(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    void handleStringPatternChanged(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    void handleStringFormatChanged(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    void handleCombineOperatorChanged(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    void handleCombineSubschemaNoMatch(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    default void handleUnresolvedSchemaReference(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException {
        throw new IncompatibleSchemaException("Reference at " + propLocationName(context) + " could not be resolved");
    }

    void handleConstantValueChanged(SchemaCompatibilityValidationContext context) throws IncompatibleSchemaException;

    void handleConditionalSchemaDiffers(SchemaCompatibilityValidationContext context)
            throws IncompatibleSchemaException;

    void handleEnumValueAdded(SchemaCompatibilityValidationContext context, String value)
            throws IncompatibleSchemaException;

}
