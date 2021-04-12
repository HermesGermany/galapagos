package com.hermesworld.ais.galapagos.topics;

import lombok.Getter;

@Getter
public enum MessagesSize {

    VERY_SMALL(1000), SMALL(10000), NORMAL(100000), LARGE(125000), VERY_LARGE(150000);

    private final int lowerBoundary;

    MessagesSize(int lowerBoundary) {
        this.lowerBoundary = lowerBoundary;
    }
}
