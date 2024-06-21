package com.hermesworld.ais.galapagos.messages;

import java.util.MissingResourceException;

public interface MessagesService {

    /**
     * Retrieves a formatted message for the given key and arguments. This operation uses the current locale to fetch
     * the appropriate message from the resource bundle.
     *
     * @param key  The key of the message.
     * @param args The arguments used to format the message.
     * @return The formatted message.
     * @throws MissingResourceException if no message is found for the provided key.
     */
    String getMessage(String key, Object... args);
}
