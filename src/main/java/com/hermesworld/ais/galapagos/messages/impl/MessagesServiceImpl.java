package com.hermesworld.ais.galapagos.messages.impl;

import com.hermesworld.ais.galapagos.messages.MessageUtil;
import lombok.extern.slf4j.Slf4j;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

@Slf4j
public class MessagesServiceImpl implements com.hermesworld.ais.galapagos.messages.MessagesService {

    private final String packageName;

    public MessagesServiceImpl(Class<?> clazz) {
        packageName = clazz.getPackage().getName() + ".messages";
    }

    public String getMessage(String key, Object... args) {

        Locale locale = MessageUtil.getCurrentLocale();
        ResourceBundle rb = ResourceBundle.getBundle(packageName, locale);
        String message;
        try {
            message = rb.getString(key);
        } catch (MissingResourceException e) {
            log.warn("No matching message for the provided key {}", key);
            return key;
        }

        return MessageFormat.format(message, args);
    }
}
