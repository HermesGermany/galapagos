package com.hermesworld.ais.galapagos.ccloud.auth;

import org.json.JSONException;
import org.json.JSONObject;

public final class ConfluentCloudAuthUtil {

    private ConfluentCloudAuthUtil() {
    }

    public static String getApiKey(String authJson) {
        try {
            return new JSONObject(authJson).getString("apiKey");
        }
        catch (JSONException e) {
            return null;
        }
    }

}
