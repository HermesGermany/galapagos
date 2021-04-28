package com.hermesworld.ais.galapagos.security.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.keycloak.KeycloakSecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.actuate.audit.InMemoryAuditEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * This implementation of the {@link AuditEventRepository} interface just extends the in-memory implementation of Spring
 * Boot Actuator and logs each added AuditEvent to the special logger <code>galapagos.audit</code>. The AuditEvent is
 * wrapped in an info structure also containing information about the current HTTP request, if any. The info structure
 * is logged in JSON format.
 *
 * @author AlbrechtFlo
 *
 */
@Component
public class AuditEventRepositoryImpl extends InMemoryAuditEventRepository {

    private static final Logger LOG = LoggerFactory.getLogger(AuditEventRepositoryImpl.class.getName());

    private final ObjectMapper objectMapper;

    public AuditEventRepositoryImpl() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setConfig(
                this.objectMapper.getSerializationConfig().without(SerializationFeature.FAIL_ON_EMPTY_BEANS));

        SimpleModule mapperModule = new SimpleModule();
        mapperModule.addSerializer(new KeycloakSecurityContextSerializer());
        this.objectMapper.registerModule(mapperModule);
    }

    @Override
    public void add(AuditEvent event) {
        super.add(event);

        HttpServletRequest request = null;
        try {
            if (RequestContextHolder.currentRequestAttributes() instanceof ServletRequestAttributes) {
                request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            }
        }
        catch (IllegalStateException e) {
            // OK, no request
            request = null;
        }

        Map<String, Object> logEntry = new HashMap<>();
        if (request != null) {
            logEntry.put("request", toLogMap(request));
        }
        logEntry.put("event", event);

        try {
            LOG.info(this.objectMapper.writeValueAsString(logEntry));
        }
        catch (JsonProcessingException e) {
            // critical, as Audit log entry missing
            throw new RuntimeException("Could not serialize Audit log entry", e);
        }
    }

    private static Map<String, String> toLogMap(HttpServletRequest request) {
        Map<String, String> result = new HashMap<>();
        result.put("url", request.getRequestURL().toString());
        result.put("method", request.getMethod());
        result.put("path", request.getPathInfo());
        result.put("clientIp", request.getRemoteAddr());
        return result;
    }

    private static class KeycloakSecurityContextSerializer extends JsonSerializer<KeycloakSecurityContext> {

        @Override
        public void serialize(KeycloakSecurityContext value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeObject(Collections.emptyMap());
        }

        @Override
        public Class<KeycloakSecurityContext> handledType() {
            return KeycloakSecurityContext.class;
        }

    }

}
