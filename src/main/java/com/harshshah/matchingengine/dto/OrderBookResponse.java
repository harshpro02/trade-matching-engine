package com.harshshah.matchingengine.dto;

import java.util.List;

/**
 * The current state of one symbol's book.
 *
 * <p>{@code bids} are ordered best first, meaning highest price first. {@code asks} are also
 * ordered best first, which for the sell side means lowest price first. So the top of each
 * list is the side's most aggressive resting order, and the two lists cross exactly when
 * {@code bids[0].price >= asks[0].price} - which, in a book the engine has finished with,
 * can never be true.
 */
public record OrderBookResponse(String symbol,
                                List<PriceLevelResponse> bids,
                                List<PriceLevelResponse> asks) {
}
