package com.harshshah.matchingengine.dto;

import com.harshshah.matchingengine.domain.Order;
import com.harshshah.matchingengine.domain.OrderStatus;
import com.harshshah.matchingengine.domain.OrderType;
import com.harshshah.matchingengine.domain.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An order's current state.
 *
 * <p>{@code sequenceNumber} is exposed because it is the book's actual ordering key, so a
 * caller comparing two orders' queue positions needs it; {@code createdAt} would give the
 * wrong answer for two orders that arrived in the same millisecond.
 */
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
