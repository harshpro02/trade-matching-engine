package com.harshshah.matchingengine.domain;

/**
 * Which way an order trades. Determines what counts as a "better" price:
 * for BUY a higher price is more aggressive, for SELL a lower price is.
 */
public enum Side {
    BUY,
    SELL;

    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }
}
