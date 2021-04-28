package com.hermesworld.ais.galapagos.ccloud.apiclient;

public class ConfluentApiException extends RuntimeException {

    public ConfluentApiException(String message) {
        super(message);
    }

    public ConfluentApiException(String message, Throwable cause) {
        super(message, cause);
    }

}
