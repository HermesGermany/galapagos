package com.hermesworld.ais.galapagos.ccloud.apiclient;

public class LoginException extends ConfluentApiException {

    public LoginException(String message) {
        super(message);
    }

    public LoginException(String message, Throwable cause) {
        super(message, cause);
    }

}
