package com.eofp.order.application;

import java.util.UUID;

/** Also thrown when the order belongs to someone else, so its existence is not revealed. */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
    }
}
