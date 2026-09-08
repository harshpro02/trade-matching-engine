package com.harshshah.matchingengine.dto;

import java.math.BigDecimal;

/**
 * One aggregated price level of the book: the total quantity resting at {@code price},
 * and how many separate orders make it up.
 */
public record PriceLevelResponse(BigDecimal price, long quantity, int orderCount) {
}
