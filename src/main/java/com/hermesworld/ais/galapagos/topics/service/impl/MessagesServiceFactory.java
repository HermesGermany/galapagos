package com.hermesworld.ais.galapagos.topics.service.impl;

import org.springframework.stereotype.Component;

@Component
public class MessagesServiceFactory {


    public MessagesService getMessageService(Class<?> clazz) {
        return new MessagesService(clazz);
    }
}
