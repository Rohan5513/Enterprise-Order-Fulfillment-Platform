package com.eofp.order.application;

import com.eofp.order.domain.Order;
import com.eofp.order.infrastructure.persistence.OrderRepository;
import com.eofp.order.infrastructure.persistence.OrderStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;

    public OrderQueryService(OrderRepository orderRepository,
                             OrderStatusHistoryRepository historyRepository) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional(readOnly = true)
    public OrderDetails getOrder(UUID orderId, UUID customerId) {
        // Someone else's order looks exactly like a missing one (docs/06 section 2)
        Order order = orderRepository.findWithItemsById(orderId)
                .filter(found -> found.getCustomerId().equals(customerId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return new OrderDetails(order, historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId));
    }
}
