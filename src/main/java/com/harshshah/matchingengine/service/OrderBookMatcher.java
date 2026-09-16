package com.harshshah.matchingengine.service;

import com.harshshah.matchingengine.domain.Order;
import com.harshshah.matchingengine.domain.OrderType;
import com.harshshah.matchingengine.domain.Side;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class OrderBookMatcher {
    private OrderBookMatcher() {
    }

    public static List<Fill> match(Order incoming, List<Order> restingBestFirst) {
        Objects.requireNonNull(incoming, "incoming order is required");
        Objects.requireNonNull(restingBestFirst, "resting orders are required");

        long remaining = incoming.getRemainingQuantity();
        if (remaining <= 0) {
            return List.of();
        }

        List<Fill> fills = new ArrayList<>();
        for (Order resting : restingBestFirst) {
            if (remaining == 0) {
                break;
            }
            requireMatchable(incoming, resting);
            if (!crosses(incoming, resting)) {
                // Book is best-first, so nothing behind this one can cross either.
                break;
            }

            long quantity = Math.min(remaining, resting.getRemainingQuantity());
            if (quantity <= 0) {
                continue;
            }
            fills.add(new Fill(resting, quantity, resting.getPrice()));
            remaining -= quantity;
        }
        return List.copyOf(fills);
    }

    static boolean crosses(Order incoming, Order resting) {
        if (incoming.getType() == OrderType.MARKET) {
            return true;
        }
        BigDecimal incomingPrice = incoming.getPrice();
        BigDecimal restingPrice = resting.getPrice();
        return incoming.getSide() == Side.BUY
                ? incomingPrice.compareTo(restingPrice) >= 0
                : incomingPrice.compareTo(restingPrice) <= 0;
    }

    private static void requireMatchable(Order incoming, Order resting) {
        if (!incoming.getSymbol().equals(resting.getSymbol())) {
            throw new IllegalArgumentException("cannot match " + incoming.getSymbol()
                    + " against a book for " + resting.getSymbol());
        }
        if (resting.getSide() != incoming.getSide().opposite()) {
            throw new IllegalArgumentException("resting book must be the opposite side of "
                    + incoming.getSide() + ", found " + resting.getSide());
        }
        if (resting.getPrice() == null) {
            throw new IllegalArgumentException("a resting order must carry a price");
        }
    }
}
