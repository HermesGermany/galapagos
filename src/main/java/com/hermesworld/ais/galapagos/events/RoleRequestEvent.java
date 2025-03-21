package com.hermesworld.ais.galapagos.events;

import com.hermesworld.ais.galapagos.security.roles.UserRoleData;
import lombok.Getter;

@Getter
public class RoleRequestEvent extends AbstractGalapagosEvent {

    private final UserRoleData request;

    public RoleRequestEvent(GalapagosEventContext context, UserRoleData request) {
        super(context);
        this.request = request;
    }

}
