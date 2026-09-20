package com.eofp.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The order aggregate. All state changes go through the methods below, so the rules in
 * docs/09-order-saga.md cannot be bypassed. JPA annotations are a pragmatic trade-off;
 * the rules themselves are plain Java.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "order_number", nullable = false, updatable = false, length = 30)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 30)
    private CancellationReason cancellationReason;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    // The column is CHAR(3); tell Hibernate so schema validation does not expect VARCHAR
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
    private String currencyCode;

    // Optimistic locking: a concurrent update with a stale version is rejected
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
        // required by JPA
    }

    public static Order create(String orderNumber, UUID customerId, String currencyCode) {
        Order order = new Order();
        order.id = UUID.randomUUID();     // assigned by the application, before any insert
        order.orderNumber = Objects.requireNonNull(orderNumber);
        order.customerId = Objects.requireNonNull(customerId);
        order.currencyCode = Objects.requireNonNull(currencyCode);
        order.status = OrderStatus.CREATED;
        order.totalAmount = BigDecimal.ZERO.setScale(2);
        order.createdAt = Instant.now();
        order.updatedAt = order.createdAt;
        return order;
    }

    public OrderItem addItem(UUID productId, int quantity, BigDecimal unitPrice) {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("Items can only be added while the order is CREATED");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        OrderItem item = new OrderItem(UUID.randomUUID(), this, productId, quantity, unitPrice);
        items.add(item);
        totalAmount = totalAmount.add(item.getTotalPrice());
        return item;
    }

    public void markPaymentPending() {
        transitionTo(OrderStatus.PAYMENT_PENDING);
    }

    public void confirm() {
        transitionTo(OrderStatus.CONFIRMED);
    }

    public void cancel(CancellationReason reason) {
        Objects.requireNonNull(reason);
        transitionTo(OrderStatus.CANCELLED);
        this.cancellationReason = reason;
    }

    private void transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidOrderTransitionException(status, target);
        }
        this.status = target;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public CancellationReason getCancellationReason() {
        return cancellationReason;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
