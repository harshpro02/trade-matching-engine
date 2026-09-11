package com.harshshah.matchingengine.service;

/**
 * Thrown when an order names a symbol that has no row in {@code instruments}.
 *
 * <p>This is not merely a lookup failure. The instruments row is what matching locks, so a
 * symbol without one cannot be matched safely at all, and the foreign key from
 * {@code orders.symbol} would reject the insert regardless. Failing here turns that into a
 * 404 with a usable message instead of a constraint violation.
 */
public class UnknownSymbolException extends RuntimeException {

    public UnknownSymbolException(String symbol) {
        super("unknown symbol: " + symbol);
    }
}
