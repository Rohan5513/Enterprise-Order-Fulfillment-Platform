package com.eofp.order.application;

import com.eofp.order.domain.Order;
import com.eofp.order.domain.OrderStatusHistory;

import java.util.List;

public record OrderDetails(Order order, List<OrderStatusHistory> history) {
}
