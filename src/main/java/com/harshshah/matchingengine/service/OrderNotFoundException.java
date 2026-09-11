package com.harshshah.matchingengine.service;

import java.util.UUID;

/** Thrown when an order id does not exist. */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID orderId) {
        super("no such order: " + orderId);
    }
}
