package com.harshshah.matchingengine.dto;

import com.harshshah.matchingengine.domain.Position;

import java.math.BigDecimal;

/**
 * What an account holds in one symbol.
 *
 * <p>{@code quantity} is signed: positive long, negative short, zero flat. {@code realisedPnl}
 * is money that has actually been made, never a mark-to-market figure; a flat position with
 * a non-zero realised P&amp;L is the normal end state of a round trip, not an anomaly.
 */
public record PositionResponse(String symbol,
                               long quantity,
                               BigDecimal averageCost,
                               BigDecimal realisedPnl) {

    public static PositionResponse from(Position position) {
        return new PositionResponse(position.getSymbol(), position.getQuantity(),
                position.getAverageCost(), position.getRealisedPnl());
    }
}
