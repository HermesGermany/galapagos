package com.hermesworld.ais.galapagos.topics;

import lombok.Getter;

@Getter
public enum MessagesSize {

    VERY_SMALL(0), SMALL(1000), NORMAL(10000), LARGE(100000), VERY_LARGE(1000000);

    private final int lowerBoundary;

    MessagesSize(int lowerBoundary) {
        this.lowerBoundary = lowerBoundary;
    }
}

