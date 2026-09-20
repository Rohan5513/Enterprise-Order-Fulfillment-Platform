package com.eofp.order.domain;

public class InvalidOrderTransitionException extends RuntimeException {

    private final OrderStatus from;
    private final OrderStatus to;

    public InvalidOrderTransitionException(OrderStatus from, OrderStatus to) {
        super("Order cannot move from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public OrderStatus getFrom() {
        return from;
    }

    public OrderStatus getTo() {
        return to;
    }
}
