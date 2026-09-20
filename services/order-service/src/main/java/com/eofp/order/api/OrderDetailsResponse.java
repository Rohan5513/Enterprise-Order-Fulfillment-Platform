package com.eofp.order.api;

import com.eofp.order.application.OrderDetails;
import com.eofp.order.domain.CancellationReason;
import com.eofp.order.domain.Order;
import com.eofp.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** API shape of an order. Entities are never returned directly, so the schema can change freely. */
public record OrderDetailsResponse(
        UUID id,
        String orderNumber,
        UUID customerId,
        OrderStatus orderStatus,
        CancellationReason cancellationReason,
        BigDecimal totalAmount,
        String currencyCode,
        List<Item> items,
        List<HistoryEntry> history,
        Instant createdAt,
        Instant updatedAt) {

    public record Item(UUID productId, int quantity, BigDecimal unitPrice, BigDecimal totalPrice) {
    }

    public record HistoryEntry(OrderStatus from, OrderStatus to, Instant at) {
    }

    static OrderDetailsResponse from(OrderDetails details) {
        Order order = details.order();
        return new OrderDetailsResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getStatus(),
                order.getCancellationReason(),
                order.getTotalAmount(),
                order.getCurrencyCode(),
                order.getItems().stream()
                        .map(item -> new Item(item.getProductId(), item.getQuantity(),
                                item.getUnitPrice(), item.getTotalPrice()))
                        .toList(),
                details.history().stream()
                        .map(entry -> new HistoryEntry(entry.getFromStatus(), entry.getToStatus(),
                                entry.getCreatedAt()))
                        .toList(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
