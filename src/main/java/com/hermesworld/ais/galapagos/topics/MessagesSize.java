package com.hermesworld.ais.galapagos.topics;

import lombok.Getter;

@Getter
public enum MessagesSize {

    VERY_SMALL(0), SMALL(10000), NORMAL(100000), LARGE(1000000), VERY_LARGE(1000000);

    private final int lowerBoundary;

    MessagesSize(int lowerBoundary) {
        this.lowerBoundary = lowerBoundary;
    }
}
