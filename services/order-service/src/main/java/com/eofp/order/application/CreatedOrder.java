package com.eofp.order.application;

import com.eofp.order.domain.Order;
import com.eofp.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatedOrder(UUID orderId, String orderNumber, OrderStatus orderStatus,
                           BigDecimal totalAmount, String currencyCode) {

    static CreatedOrder from(Order order) {
        return new CreatedOrder(order.getId(), order.getOrderNumber(), order.getStatus(),
                order.getTotalAmount(), order.getCurrencyCode());
    }
}
