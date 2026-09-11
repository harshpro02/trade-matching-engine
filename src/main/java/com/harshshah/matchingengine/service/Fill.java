package com.harshshah.matchingengine.service;

import com.harshshah.matchingengine.domain.Order;

import java.math.BigDecimal;

/**
 * One decision to trade: {@code quantity} units against {@code restingOrder} at {@code price}.
 *
 * <p>This is what the matcher returns and it is deliberately inert. It records a decision
 * without carrying it out, so the matcher can be a pure function over a book snapshot and
 * everything that mutates state - decrementing both orders, writing the trade, moving both
 * positions - stays in the service, inside the transaction, where it can be rolled back.
 *
 * <p>{@code price} is always the resting order's price, never the incoming order's.
 */
public record Fill(Order restingOrder, long quantity, BigDecimal price) {

    public Fill {
        if (quantity <= 0) {
            throw new IllegalArgumentException("fill quantity must be positive, was " + quantity);
        }
    }
}
