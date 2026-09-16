package com.harshshah.matchingengine.service;

import com.harshshah.matchingengine.domain.Order;

import java.math.BigDecimal;

public record Fill(Order restingOrder, long quantity, BigDecimal price) {
    public Fill {
        if (quantity <= 0) {
            throw new IllegalArgumentException("fill quantity must be positive, was " + quantity);
        }
    }
}
