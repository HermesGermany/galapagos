package com.hermesworld.ais.galapagos.schemas;

public class IncompatibleSchemaException extends Exception {

    private static final long serialVersionUID = -35786123925478756L;

    public IncompatibleSchemaException(String message, Throwable cause) {
        super(message, cause);
    }

    public IncompatibleSchemaException(String message) {
        super(message);
    }

}
