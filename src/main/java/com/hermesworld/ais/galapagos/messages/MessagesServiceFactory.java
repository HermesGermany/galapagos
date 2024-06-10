package com.hermesworld.ais.galapagos.messages;

import com.hermesworld.ais.galapagos.messages.impl.MessagesServiceImpl;
import org.springframework.stereotype.Component;

@Component
public class MessagesServiceFactory {

    public MessagesServiceImpl getMessagesService(Class<?> clazz) {
        return new MessagesServiceImpl(clazz);
    }
}
