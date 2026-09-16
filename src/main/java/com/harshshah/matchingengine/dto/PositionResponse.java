package com.harshshah.matchingengine.dto;

import com.harshshah.matchingengine.domain.Position;

import java.math.BigDecimal;

public record PositionResponse(String symbol,
                               long quantity,
                               BigDecimal averageCost,
                               BigDecimal realisedPnl) {
    public static PositionResponse from(Position position) {
        return new PositionResponse(position.getSymbol(), position.getQuantity(),
                position.getAverageCost(), position.getRealisedPnl());
    }
}
