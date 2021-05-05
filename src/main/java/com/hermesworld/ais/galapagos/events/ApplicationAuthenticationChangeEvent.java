package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import lombok.Getter;
import org.json.JSONObject;

@Getter
public class ApplicationAuthenticationChangeEvent extends ApplicationEvent {

    private final JSONObject oldAuthentication;

    private final JSONObject newAuthentication;

    public ApplicationAuthenticationChangeEvent(GalapagosEventContext context, ApplicationMetadata metadata,
            JSONObject oldAuthentication, JSONObject newAuthentication) {
        super(context, metadata);
        this.oldAuthentication = oldAuthentication;
        this.newAuthentication = newAuthentication;
    }
}
