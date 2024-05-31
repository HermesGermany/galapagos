package com.hermesworld.ais.galapagos.topics.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

@Slf4j
public class MessagesService {

    public String getMessage(String key, Object... args) {

        Locale locale = getLocale();
        String packageName = this.getClass().getPackage().getName();
        String bundleName = packageName + ".messages";
        ResourceBundle rb = ResourceBundle.getBundle(bundleName, locale);
        String message;
        try {
            message = rb.getString(key);
        } catch (MissingResourceException e) {
            log.warn("No matching message for the provided key {}", key);
            return key;
        }

        return MessageFormat.format(message, args);
    }


    private Locale getLocale() {

        try {
            if (RequestContextHolder.currentRequestAttributes() instanceof ServletRequestAttributes) {
                HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
                return RequestContextUtils.getLocale(request);
            }
        } catch (IllegalStateException e) {
            // OK, no request
            log.trace("No current request found for MessageService, using default locale");
        }
        return Locale.getDefault();
    }
}
