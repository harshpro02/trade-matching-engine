package com.harshshah.matchingengine.dto;

import com.harshshah.matchingengine.domain.Order;
import com.harshshah.matchingengine.domain.OrderStatus;

import java.util.List;
import java.util.UUID;

/**
 * The outcome of one submission: what the order became, and every trade it caused.
 *
 * <p>The trades are returned inline rather than left to a follow-up request because the
 * submitter cannot otherwise tell a resting order from one that filled instantly, and the
 * distinction is the whole answer to "what happened to my order".
 */
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
