package com.harshshah.matchingengine.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of an order.
 *
 * <p>OPEN and PARTIALLY_FILLED are the two "resting" states: an order in either state
 * still has remaining quantity sitting on the book and can be matched or cancelled.
 * FILLED and CANCELLED are terminal.
 */
public enum OrderStatus {
    OPEN,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED;

    /** The statuses in which an order is still on the book and available to match. */
    public static final Set<OrderStatus> RESTING = EnumSet.of(OPEN, PARTIALLY_FILLED);

    /** True when the order still has quantity available to match against. */
    public boolean isResting() {
        return this == OPEN || this == PARTIALLY_FILLED;
    }

    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED;
    }
}
