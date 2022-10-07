package com.hermesworld.ais.galapagos.ccloud.auth;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;

public final class ConfluentCloudAuthUtil {

    private ConfluentCloudAuthUtil() {
    }

    public static String getApiKey(String authJson) {
        try {
            return new JSONObject(authJson).getString(ConfluentCloudAuthenticationModule.JSON_API_KEY);
        }
        catch (JSONException e) {
            return null;
        }
    }

    public static Instant getExpiresAt(String authJson) {
        try {
            return Instant
                    .parse(new JSONObject(authJson).getString(ConfluentCloudAuthenticationModule.JSON_EXPIRES_AT));
        }
        catch (DateTimeParseException | JSONException e) {
            return null;
        }
    }

}
