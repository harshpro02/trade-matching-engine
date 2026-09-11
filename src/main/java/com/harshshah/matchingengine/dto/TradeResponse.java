package com.harshshah.matchingengine.dto;

import com.harshshah.matchingengine.domain.Trade;

import java.math.BigDecimal;
import java.util.UUID;

/** One execution, as reported back to the submitter of an order. */
public record TradeResponse(UUID tradeId, BigDecimal price, long quantity) {

    public static TradeResponse from(Trade trade) {
        return new TradeResponse(trade.getId(), trade.getPrice(), trade.getQuantity());
    }
}
