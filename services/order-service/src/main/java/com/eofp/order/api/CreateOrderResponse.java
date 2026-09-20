package com.eofp.order.api;

import com.eofp.order.application.CreatedOrder;
import com.eofp.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderResponse(UUID orderId, String orderNumber, OrderStatus orderStatus,
                                  BigDecimal totalAmount, String currencyCode) {

    static CreateOrderResponse from(CreatedOrder created) {
        return new CreateOrderResponse(created.orderId(), created.orderNumber(), created.orderStatus(),
                created.totalAmount(), created.currencyCode());
    }
}
