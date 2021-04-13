package com.hermesworld.ais.galapagos.topics;

import lombok.Getter;

@Getter
public enum MessagesPerDay {

    FEW(0), NORMAL(100000), MANY(1000000), VERY_MANY(1000000);

    private final int lowerBoundary;

    MessagesPerDay(int lowerBoundary) {
        this.lowerBoundary = lowerBoundary;
    }

}
