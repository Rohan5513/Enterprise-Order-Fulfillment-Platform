package com.eofp.order.application;

import com.eofp.order.application.OrderRequestRejectedException.Reason;
import com.eofp.order.domain.Order;
import com.eofp.order.domain.OrderStatus;
import com.eofp.order.domain.OrderStatusHistory;
import com.eofp.order.infrastructure.persistence.IdempotencyKeyRepository;
import com.eofp.order.infrastructure.persistence.IdempotencyKeyRepository.StoredKey;
import com.eofp.order.infrastructure.persistence.OrderNumberGenerator;
import com.eofp.order.infrastructure.persistence.OrderRepository;
import com.eofp.order.infrastructure.persistence.OrderStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Creates an order exactly once per Idempotency-Key (docs/10 section 2).
 *
 * Order of work, on purpose:
 *   1. cheap validation, then a fast-path lookup: a retry of a finished request is answered from
 *      the stored response without touching the Product Service
 *   2. the remote call, with no transaction open
 *   3. one short transaction that claims the key, creates the order and stores the response
 *
 * The lookup in step 1 is only an optimisation. The unique constraint in step 3 is the authority.
 */
@Service
public class CreateOrderService {

    private static final Duration KEY_LIFETIME = Duration.ofHours(24);
    private static final int MAX_KEY_LENGTH = 100;
    private static final int CREATED = 201;

    private final ProductCatalog productCatalog;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final IdempotencyKeyRepository keyRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public CreateOrderService(ProductCatalog productCatalog,
                              OrderRepository orderRepository,
                              OrderStatusHistoryRepository historyRepository,
                              IdempotencyKeyRepository keyRepository,
                              OrderNumberGenerator orderNumberGenerator,
                              TransactionTemplate transactionTemplate,
                              JsonMapper jsonMapper,
                              Clock clock) {
        this.productCatalog = productCatalog;
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.keyRepository = keyRepository;
        this.orderNumberGenerator = orderNumberGenerator;
        this.transactionTemplate = transactionTemplate;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    // Deliberately not @Transactional: the transaction starts only around the database work below.
    public CreateOrderResult create(UUID customerId, String idempotencyKey, List<RequestedItem> items) {
        validateKey(idempotencyKey);
        rejectDuplicateProducts(items);
        String requestHash = RequestHasher.hash(items);

        // Fast path for retries of a request that already completed
        Optional<StoredKey> existing = keyRepository.find(customerId, idempotencyKey);
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), requestHash);
        }

        // Remote call BEFORE any transaction is open
        Set<UUID> productIds = new HashSet<>();
        items.forEach(item -> productIds.add(item.productId()));
        Map<UUID, ProductInfo> products = productCatalog.findByIds(productIds);
        String currency = validateProducts(items, products);

        CreateOrderResult result = transactionTemplate.execute(status ->
                createOnce(customerId, idempotencyKey, requestHash, currency, items, products));
        return Objects.requireNonNull(result);
    }

    private CreateOrderResult createOnce(UUID customerId, String idempotencyKey, String requestHash,
                                         String currency, List<RequestedItem> items,
                                         Map<UUID, ProductInfo> products) {
        UUID orderId = UUID.randomUUID();

        // Claim the key. If a concurrent identical request holds it, this waits for that
        // transaction to finish, then reports the key as already taken.
        boolean claimed = keyRepository.tryInsert(customerId, idempotencyKey, requestHash, orderId,
                clock.instant().plus(KEY_LIFETIME));
        if (!claimed) {
            StoredKey stored = keyRepository.find(customerId, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotency key vanished after conflict"));
            return replayOrReject(stored, requestHash);
        }

        CreatedOrder created = persist(orderId, customerId, currency, items, products);
        keyRepository.complete(customerId, idempotencyKey, CREATED, jsonMapper.writeValueAsString(created));
        return new CreateOrderResult(created, false);
    }

    private CreatedOrder persist(UUID orderId, UUID customerId, String currency,
                                 List<RequestedItem> items, Map<UUID, ProductInfo> products) {
        Order order = Order.create(orderId, orderNumberGenerator.next(), customerId, currency);
        for (RequestedItem item : items) {
            order.addItem(item.productId(), item.quantity(), products.get(item.productId()).unitPrice());
        }
        orderRepository.save(order);
        historyRepository.save(OrderStatusHistory.record(
                order.getId(), null, OrderStatus.CREATED, "ORDER_CREATED", clock.instant()));
        return CreatedOrder.from(order);
    }

    private CreateOrderResult replayOrReject(StoredKey stored, String requestHash) {
        if (!stored.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        if (stored.responseBody() == null) {
            // Cannot happen: the key and its response are committed together
            throw new IllegalStateException("Idempotency key has no stored response");
        }
        return new CreateOrderResult(jsonMapper.readValue(stored.responseBody(), CreatedOrder.class), true);
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new OrderRequestRejectedException(Reason.INVALID_IDEMPOTENCY_KEY,
                    "Idempotency-Key must be between 1 and " + MAX_KEY_LENGTH + " characters");
        }
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
}
