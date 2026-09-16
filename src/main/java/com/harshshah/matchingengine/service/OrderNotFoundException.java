package com.harshshah.matchingengine.service;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID orderId) {
        super("no such order: " + orderId);
    }
}
