package com.harshshah.matchingengine.dto;

import com.harshshah.matchingengine.domain.OrderType;
import com.harshshah.matchingengine.domain.Side;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record SubmitOrderRequest(@NotNull UUID accountId,
                                 @NotBlank String symbol,
                                 @NotNull Side side,
                                 @NotNull OrderType type,
                                 BigDecimal price,
                                 @Positive long quantity) {
    @AssertTrue(message = "LIMIT order requires a positive price, MARKET order must not carry one")
    public boolean isPriceConsistentWithType() {
        if (type == null) {
            return true;
        }
        return type == OrderType.LIMIT
                ? price != null && price.signum() > 0
                : price == null;
    }
}
