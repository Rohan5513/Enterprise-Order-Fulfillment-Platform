package com.eofp.order.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static com.eofp.order.domain.OrderStatus.CANCELLED;
import static com.eofp.order.domain.OrderStatus.CONFIRMED;
import static com.eofp.order.domain.OrderStatus.CREATED;
import static com.eofp.order.domain.OrderStatus.PAYMENT_PENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderStatusTest {

    // Mirrors docs/09-order-saga.md section 4. Anything not listed here must be rejected.
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            CREATED, Set.of(PAYMENT_PENDING, CANCELLED),
            PAYMENT_PENDING, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(),
            CANCELLED, Set.of());

    @Test
    void onlyTheTransitionsInTheSagaDesignAreAllowed() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                boolean expected = ALLOWED.get(from).contains(to);
                assertEquals(expected, from.canTransitionTo(to), from + " -> " + to);
            }
        }
    }
}
