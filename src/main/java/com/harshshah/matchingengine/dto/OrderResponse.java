package com.harshshah.matchingengine.dto;

import com.harshshah.matchingengine.domain.Order;
import com.harshshah.matchingengine.domain.OrderStatus;
import com.harshshah.matchingengine.domain.OrderType;
import com.harshshah.matchingengine.domain.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(UUID orderId,
                            UUID accountId,
                            String symbol,
                            Side side,
                            OrderType type,
                            BigDecimal price,
                            long quantity,
                            long filledQuantity,
                            long remainingQuantity,
                            OrderStatus status,
                            Instant createdAt,
                            Long sequenceNumber) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getAccountId(), order.getSymbol(),
                order.getSide(), order.getType(), order.getPrice(), order.getQuantity(),
                order.filledQuantity(), order.getRemainingQuantity(), order.getStatus(),
                order.getCreatedAt(), order.getSequenceNumber());
    }
}
