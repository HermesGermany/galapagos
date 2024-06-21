package com.hermesworld.ais.galapagos.messages;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.Locale;

@Slf4j
public class MessageUtil {

    public static Locale getCurrentLocale() {

        try {
            if (RequestContextHolder.currentRequestAttributes() instanceof ServletRequestAttributes) {
                HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder
                        .currentRequestAttributes()).getRequest();
                return RequestContextUtils.getLocale(request);
            }
        }
        catch (IllegalStateException e) {
            // OK, no request
            log.trace("No current request found for MessageService, using default locale");
        }
        return Locale.getDefault();
    }
}
