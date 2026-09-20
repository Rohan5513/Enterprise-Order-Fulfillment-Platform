package com.eofp.order.application;

import com.eofp.order.application.OrderRequestRejectedException.Reason;
import com.eofp.order.domain.Order;
import com.eofp.order.domain.OrderStatus;
import com.eofp.order.domain.OrderStatusHistory;
import com.eofp.order.infrastructure.persistence.OrderNumberGenerator;
import com.eofp.order.infrastructure.persistence.OrderRepository;
import com.eofp.order.infrastructure.persistence.OrderStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Creates an order. The steps are ordered deliberately (docs/10 section 2.3):
 * cheap validation, then the remote call, and only then a short database transaction,
 * so no connection or lock is held while waiting on another service.
 */
@Service
public class CreateOrderService {

    private final ProductCatalog productCatalog;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public CreateOrderService(ProductCatalog productCatalog,
                              OrderRepository orderRepository,
                              OrderStatusHistoryRepository historyRepository,
                              OrderNumberGenerator orderNumberGenerator,
                              TransactionTemplate transactionTemplate,
                              Clock clock) {
        this.productCatalog = productCatalog;
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.orderNumberGenerator = orderNumberGenerator;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    // Deliberately not @Transactional: the transaction starts only around the database work below.
    public CreatedOrder create(UUID customerId, List<RequestedItem> items) {
        // 1. Cheap checks, no I/O
        rejectDuplicateProducts(items);

        // 2. Remote call BEFORE any transaction is open
        Set<UUID> productIds = new HashSet<>();
        items.forEach(item -> productIds.add(item.productId()));
        Map<UUID, ProductInfo> products = productCatalog.findByIds(productIds);
        String currency = validateProducts(items, products);

        // 3. One short local transaction
        CreatedOrder created = transactionTemplate.execute(status ->
                persist(customerId, currency, items, products));
        return Objects.requireNonNull(created);
    }

    private static void rejectDuplicateProducts(List<RequestedItem> items) {
        Set<UUID> seen = new HashSet<>();
        for (RequestedItem item : items) {
            if (!seen.add(item.productId())) {
                throw new OrderRequestRejectedException(Reason.DUPLICATE_PRODUCT,
                        "Product listed more than once: " + item.productId());
            }
        }
    }

    /** Every product must exist, be active, and share one currency. Returns that currency. */
    private static String validateProducts(List<RequestedItem> items, Map<UUID, ProductInfo> products) {
        Set<String> currencies = new HashSet<>();
        for (RequestedItem item : items) {
            ProductInfo product = products.get(item.productId());
            if (product == null || !product.active()) {
                throw new OrderRequestRejectedException(Reason.PRODUCT_NOT_AVAILABLE,
                        "Product not available: " + item.productId());
            }
            currencies.add(product.currencyCode());
        }
        if (currencies.size() > 1) {
            throw new OrderRequestRejectedException(Reason.MIXED_CURRENCY,
                    "All items in an order must use the same currency");
        }
        return currencies.iterator().next();
    }

    private CreatedOrder persist(UUID customerId, String currency, List<RequestedItem> items,
                                 Map<UUID, ProductInfo> products) {
        Order order = Order.create(orderNumberGenerator.next(), customerId, currency);
        for (RequestedItem item : items) {
            order.addItem(item.productId(), item.quantity(), products.get(item.productId()).unitPrice());
        }
        orderRepository.save(order);
        historyRepository.save(OrderStatusHistory.record(
                order.getId(), null, OrderStatus.CREATED, "ORDER_CREATED", clock.instant()));
        return CreatedOrder.from(order);
    }
}
