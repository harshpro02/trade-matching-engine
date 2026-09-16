package com.harshshah.matchingengine.dto;

import com.harshshah.matchingengine.domain.Order;
import com.harshshah.matchingengine.domain.OrderStatus;

import java.util.List;
import java.util.UUID;

public record SubmitOrderResponse(UUID orderId,
                                  OrderStatus status,
                                  long filledQuantity,
                                  long remainingQuantity,
                                  List<TradeResponse> trades) {
    public static SubmitOrderResponse of(Order order, List<TradeResponse> trades) {
        return new SubmitOrderResponse(order.getId(), order.getStatus(),
                order.filledQuantity(), order.getRemainingQuantity(), trades);
    }
}
