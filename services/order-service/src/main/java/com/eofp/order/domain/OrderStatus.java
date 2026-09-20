package com.eofp.order.domain;

/**
 * Order lifecycle. Allowed transitions are defined in docs/09-order-saga.md section 4.
 * Plain Java on purpose: no Spring, no database, so the rules are trivial to unit-test.
 */
public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    CONFIRMED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case CREATED -> target == PAYMENT_PENDING || target == CANCELLED;
            case PAYMENT_PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED, CANCELLED -> false;   // terminal states
        };
    }
}
