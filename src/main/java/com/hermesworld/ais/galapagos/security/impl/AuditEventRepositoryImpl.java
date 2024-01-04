package com.hermesworld.ais.galapagos.security.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.actuate.audit.InMemoryAuditEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
@Slf4j
public class AuditEventRepositoryImpl extends InMemoryAuditEventRepository {

    private final ObjectMapper objectMapper;

    public AuditEventRepositoryImpl() {
        this.objectMapper = JsonUtil.newObjectMapper();
        this.objectMapper.setConfig(
                this.objectMapper.getSerializationConfig().without(SerializationFeature.FAIL_ON_EMPTY_BEANS));

        SimpleModule mapperModule = new SimpleModule();
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
            log.trace("No current request found for Audit log, not including request information");
        }

        Map<String, Object> logEntry = new HashMap<>();
        if (request != null) {
            logEntry.put("request", toLogMap(request));
        }
        logEntry.put("event", event);

        try {
            log.info(this.objectMapper.writeValueAsString(logEntry));
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

}
