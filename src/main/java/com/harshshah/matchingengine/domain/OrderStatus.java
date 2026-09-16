package com.harshshah.matchingengine.domain;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    OPEN,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED;

    public static final Set<OrderStatus> RESTING = EnumSet.of(OPEN, PARTIALLY_FILLED);

    public boolean isResting() {
        return this == OPEN || this == PARTIALLY_FILLED;
    }

    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED;
    }
}
