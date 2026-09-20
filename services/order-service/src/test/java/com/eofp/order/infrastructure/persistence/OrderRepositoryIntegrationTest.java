package com.eofp.order.infrastructure.persistence;

import com.eofp.order.AbstractPostgresIntegrationTest;
import com.eofp.order.domain.CancellationReason;
import com.eofp.order.domain.Order;
import com.eofp.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    OrderRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static Order newOrder() {
        String number = "ORD-T-" + UUID.randomUUID().toString().substring(0, 8);
        Order order = Order.create(number, UUID.randomUUID(), "INR");
        order.addItem(UUID.randomUUID(), 2, new BigDecimal("100.00"));
        order.addItem(UUID.randomUUID(), 1, new BigDecimal("50.00"));
        return order;
    }

    @Test
    void savesAndLoadsAnOrderWithItems() {
        Order order = newOrder();
        repository.saveAndFlush(order);

        Order loaded = repository.findWithItemsById(order.getId()).orElseThrow();

        assertEquals(OrderStatus.CREATED, loaded.getStatus());
        assertEquals(2, loaded.getItems().size());
        assertEquals(0, new BigDecimal("250.00").compareTo(loaded.getTotalAmount()));
        assertEquals("INR", loaded.getCurrencyCode());
        assertEquals(0L, loaded.getVersion());
    }

    @Test
    void staleUpdateIsRejectedByOptimisticLocking() {
        Order saved = newOrder();
        repository.saveAndFlush(saved);

        // Two handlers load the same order, as two concurrent events would
        Order first = repository.findById(saved.getId()).orElseThrow();
        Order second = repository.findById(saved.getId()).orElseThrow();

        first.markPaymentPending();
        repository.saveAndFlush(first);

        second.cancel(CancellationReason.TIMEOUT);
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> repository.saveAndFlush(second));
    }

    @Test
    void databaseRejectsACancelledOrderWithoutAReason() {
        // Bypass the domain model on purpose: the database is the last line of defence
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO orders (id, order_number, customer_id, order_status, cancellation_reason,
                                    total_amount, currency_code, version, created_at, updated_at)
                VALUES (?, 'ORD-T-BADROW', ?, 'CANCELLED', NULL, 10.00, 'INR', 0, now(), now())
                """, UUID.randomUUID(), UUID.randomUUID()));
    }
}
