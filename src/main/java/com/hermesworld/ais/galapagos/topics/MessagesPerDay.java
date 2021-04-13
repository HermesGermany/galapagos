package com.hermesworld.ais.galapagos.topics;

import lombok.Getter;

@Getter
public enum MessagesPerDay {

    FEW(0), NORMAL(1000), MANY(100000), VERY_MANY(1000000);

    private final int lowerBoundary;

    MessagesPerDay(int lowerBoundary) {
        this.lowerBoundary = lowerBoundary;
    }

}
