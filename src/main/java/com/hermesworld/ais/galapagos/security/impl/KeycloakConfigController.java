package com.hermesworld.ais.galapagos.security.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class KeycloakConfigController {

    @Value("${keycloak.configurationFile}")
    private Resource keycloakConfigFile;

    @GetMapping(value = "/keycloak/config.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getKeycloakConfig() {

        try (InputStream in = keycloakConfigFile.getInputStream()) {
            String contents = new String(StreamUtils.copyToByteArray(in), StandardCharsets.UTF_8);
            JSONObject config = new JSONObject(contents);

            Map<String, Object> result = new HashMap<>();
            result.put("url", config.getString("auth-server-url"));
            result.put("realm", config.getString("realm"));
            result.put("clientId", config.getString("resource"));
            return result;
        }
        catch (IOException e) {
            log.error("Could not read Keycloak config resource", e);
            return Collections.emptyMap();
        }
    }

}
