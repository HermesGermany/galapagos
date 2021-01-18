package com.hermesworld.ais.galapagos.events;

import lombok.Getter;

@Getter
public abstract class AbstractGalapagosEvent {

    private GalapagosEventContext context;

    protected AbstractGalapagosEvent(GalapagosEventContext context) {
        this.context = context;
    }

}
