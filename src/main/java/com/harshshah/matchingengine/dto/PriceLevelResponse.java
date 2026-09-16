package com.harshshah.matchingengine.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record PriceLevelResponse(BigDecimal price, long quantity, long orderCount) {

    public static final int PRICE_SCALE = 4;

    public PriceLevelResponse {
        // A group-by projection does not carry the column scale through, so prices would
        // otherwise render as 99.0 here and 99.0000 everywhere else in the API.
        price = price.setScale(PRICE_SCALE, RoundingMode.HALF_EVEN);
    }
}
