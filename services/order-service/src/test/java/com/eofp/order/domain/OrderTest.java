package com.eofp.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    private static Order newOrder() {
        Order order = Order.create("ORD-2026-00000001", UUID.randomUUID(), "INR");
        order.addItem(UUID.randomUUID(), 2, new BigDecimal("100.00"));
        order.addItem(UUID.randomUUID(), 1, new BigDecimal("50.00"));
        return order;
    }

    @Test
    void newOrderStartsAsCreatedWithTheSumOfItsItems() {
        Order order = newOrder();

        assertEquals(OrderStatus.CREATED, order.getStatus());
        assertEquals(new BigDecimal("250.00"), order.getTotalAmount());
        assertNull(order.getCancellationReason());
    }

    @Test
    void happyPathEndsConfirmed() {
        Order order = newOrder();

        order.markPaymentPending();
        order.confirm();

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    void cancelRecordsTheReason() {
        Order order = newOrder();

        order.cancel(CancellationReason.OUT_OF_STOCK);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(CancellationReason.OUT_OF_STOCK, order.getCancellationReason());
    }

    @Test
    void cancelledOrderCannotBeConfirmed() {
        Order order = newOrder();
        order.cancel(CancellationReason.TIMEOUT);

        assertThrows(InvalidOrderTransitionException.class, order::confirm);
    }

    @Test
    void cannotConfirmWithoutPaymentPending() {
        Order order = newOrder();

        assertThrows(InvalidOrderTransitionException.class, order::confirm);
    }

    @Test
    void itemsCannotBeAddedAfterTheOrderLeavesCreated() {
        Order order = newOrder();
        order.markPaymentPending();

        assertThrows(IllegalStateException.class,
                () -> order.addItem(UUID.randomUUID(), 1, new BigDecimal("10.00")));
    }

    @Test
    void pricesWithMoreThanTwoDecimalsAreRejectedInsteadOfRounded() {
        Order order = Order.create("ORD-2026-00000002", UUID.randomUUID(), "INR");

        assertThrows(ArithmeticException.class,
                () -> order.addItem(UUID.randomUUID(), 1, new BigDecimal("10.005")));
    }
}
