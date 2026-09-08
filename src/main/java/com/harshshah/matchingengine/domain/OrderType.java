package com.harshshah.matchingengine.domain;

/**
 * LIMIT  - carries a price, will only trade at that price or better, rests on the book if unfilled.
 * MARKET - carries no price, crosses whatever liquidity exists, never rests.
 */
public enum OrderType {
    LIMIT,
    MARKET
}
