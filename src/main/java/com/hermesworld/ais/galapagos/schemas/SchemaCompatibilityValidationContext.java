package com.hermesworld.ais.galapagos.schemas;

import org.everit.json.schema.Schema;

public interface SchemaCompatibilityValidationContext {

    String getCurrentPrefix();

    Schema getOldSchema();

    Schema getNewSchema();

    Schema getCurrentNodeInOldSchema();

    Schema getCurrentNodeInNewSchema();

}
