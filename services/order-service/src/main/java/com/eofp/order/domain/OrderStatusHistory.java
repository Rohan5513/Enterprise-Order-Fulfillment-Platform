package com.eofp.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per status change of an order (docs/03 section 5.3). Written in the same transaction
 * as the change itself, so the timeline can never disagree with the order.
 */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 20)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 20)
    private OrderStatus toStatus;

    @Column(name = "trigger_event", nullable = false, updatable = false, length = 50)
    private String triggerEvent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderStatusHistory() {
        // required by JPA
    }

    public static OrderStatusHistory record(UUID orderId, OrderStatus from, OrderStatus to,
                                            String triggerEvent, Instant at) {
        OrderStatusHistory entry = new OrderStatusHistory();
        entry.id = UUID.randomUUID();
        entry.orderId = orderId;
        entry.fromStatus = from;
        entry.toStatus = to;
        entry.triggerEvent = triggerEvent;
        entry.createdAt = at;
        return entry;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public String getTriggerEvent() {
        return triggerEvent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
