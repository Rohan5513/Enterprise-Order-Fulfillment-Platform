package com.eofp.order.application;

/** The order, and whether it is the stored answer to an earlier identical request. */
public record CreateOrderResult(CreatedOrder order, boolean replayed) {
}
