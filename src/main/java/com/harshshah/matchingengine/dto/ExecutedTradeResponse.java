package com.harshshah.matchingengine.dto;

import com.harshshah.matchingengine.domain.Trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExecutedTradeResponse(UUID tradeId,
                                    String symbol,
                                    UUID buyOrderId,
                                    UUID sellOrderId,
                                    BigDecimal price,
                                    long quantity,
                                    Instant executedAt,
                                    Long sequenceNumber) {
    public static ExecutedTradeResponse from(Trade trade) {
        return new ExecutedTradeResponse(trade.getId(), trade.getSymbol(), trade.getBuyOrderId(),
                trade.getSellOrderId(), trade.getPrice(), trade.getQuantity(),
                trade.getExecutedAt(), trade.getSequenceNumber());
    }
}
