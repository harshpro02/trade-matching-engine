package com.harshshah.matchingengine.dto;

import com.harshshah.matchingengine.domain.OrderType;
import com.harshshah.matchingengine.domain.Side;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An order submission.
 *
 * <p>{@code price} is required for LIMIT and forbidden for MARKET. That rule is enforced
 * here, again in {@link com.harshshah.matchingengine.domain.Order}'s factory methods, and
 * a third time by a CHECK constraint in the schema. The repetition is deliberate: this
 * layer exists to return a 400 instead of a 500, the domain exists so the rule holds for
 * callers that never touch HTTP, and the constraint exists because the database outlives
 * every version of this application.
 */
public record SubmitOrderRequest(@NotNull UUID accountId,
                                 @NotBlank String symbol,
                                 @NotNull Side side,
                                 @NotNull OrderType type,
                                 BigDecimal price,
                                 @Positive long quantity) {

    @AssertTrue(message = "LIMIT order requires a positive price, MARKET order must not carry one")
    public boolean isPriceConsistentWithType() {
        if (type == null) {
            return true; // @NotNull reports this one; don't double-report it here.
        }
        return type == OrderType.LIMIT
                ? price != null && price.signum() > 0
                : price == null;
    }
}
