package com.eofp.order.api;

import com.eofp.order.AbstractPostgresIntegrationTest;
import com.eofp.order.domain.OrderStatus;
import com.eofp.order.domain.OrderStatusHistory;
import com.eofp.order.infrastructure.persistence.OrderStatusHistoryRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real HTTP layer, validation, business rules, idempotency and PostgreSQL together.
 * The product IDs below belong to StubProductCatalog.
 */
public class OrderApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PHONE = "11111111-1111-1111-1111-111111111111";         // 15000.00 INR
    private static final String HEADPHONES = "22222222-2222-2222-2222-222222222222";    // 2500.00 INR
    private static final String DISCONTINUED = "33333333-3333-3333-3333-333333333333";  // inactive
    private static final String DOLLAR_ITEM = "44444444-4444-4444-4444-444444444444";   // 10.00 USD

    @Autowired
    WebApplicationContext context;

    @Autowired
    OrderStatusHistoryRepository historyRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // ---------- create ----------

    @Test
    void createsAnOrderAndRecordsItsFirstStatus() throws Exception {
        UUID customer = UUID.randomUUID();

        MvcResult result = post(customer, order(item(PHONE, 2), item(HEADPHONES, 1)));

        assertEquals(201, result.getResponse().getStatus());
        String orderId = read(result, "$.data.orderId");
        assertEquals("/api/v1/orders/" + orderId, result.getResponse().getHeader("Location"));
        assertEquals("CREATED", read(result, "$.data.orderStatus"));
        assertEquals("INR", read(result, "$.data.currencyCode"));
        Number total = read(result, "$.data.totalAmount");
        assertEquals(32500.0, total.doubleValue(), 0.001);   // 2 x 15000 + 1 x 2500
        String orderNumber = read(result, "$.data.orderNumber");
        assertTrue(orderNumber.matches("ORD-\\d{4}-\\d{8}"), orderNumber);
        assertNull(result.getResponse().getHeader("Idempotent-Replayed"));

        List<OrderStatusHistory> history = historyRepository.findByOrderIdOrderByCreatedAtAsc(UUID.fromString(orderId));
        assertEquals(1, history.size());
        assertEquals(OrderStatus.CREATED, history.get(0).getToStatus());
    }

    @Test
    void rejectsARequestWithoutIdentity() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/orders")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(order(item(PHONE, 1)))).andReturn();

        assertEquals(401, result.getResponse().getStatus());
        assertEquals("UNAUTHORIZED", read(result, "$.error.code"));
    }

    @Test
    void rejectsAnEmptyItemList() throws Exception {
        MvcResult result = post(UUID.randomUUID(), "{\"items\":[]}");

        assertEquals(400, result.getResponse().getStatus());
        assertEquals("VALIDATION_FAILED", read(result, "$.error.code"));
        assertEquals("items", read(result, "$.error.details[0].field"));
    }

    @Test
    void rejectsAnInvalidQuantity() throws Exception {
        MvcResult result = post(UUID.randomUUID(), order(item(PHONE, 0)));

        assertEquals(400, result.getResponse().getStatus());
        assertEquals("items[0].quantity", read(result, "$.error.details[0].field"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        MvcResult result = post(UUID.randomUUID(), "{not json");

        assertEquals(400, result.getResponse().getStatus());
        assertEquals("VALIDATION_FAILED", read(result, "$.error.code"));
    }

    @Test
    void rejectsTheSameProductListedTwice() throws Exception {
        MvcResult result = post(UUID.randomUUID(), order(item(PHONE, 1), item(PHONE, 2)));

        assertEquals(400, result.getResponse().getStatus());
        assertEquals("VALIDATION_FAILED", read(result, "$.error.code"));
    }

    @Test
    void rejectsAnInactiveProduct() throws Exception {
        MvcResult result = post(UUID.randomUUID(), order(item(DISCONTINUED, 1)));

        assertEquals(422, result.getResponse().getStatus());
        assertEquals("PRODUCT_NOT_AVAILABLE", read(result, "$.error.code"));
    }

    @Test
    void rejectsAnUnknownProduct() throws Exception {
        MvcResult result = post(UUID.randomUUID(), order(item(UUID.randomUUID().toString(), 1)));

        assertEquals(422, result.getResponse().getStatus());
        assertEquals("PRODUCT_NOT_AVAILABLE", read(result, "$.error.code"));
    }

    @Test
    void rejectsItemsInDifferentCurrencies() throws Exception {
        MvcResult result = post(UUID.randomUUID(), order(item(PHONE, 1), item(DOLLAR_ITEM, 1)));

        assertEquals(422, result.getResponse().getStatus());
        assertEquals("MIXED_CURRENCY", read(result, "$.error.code"));
    }

    // ---------- idempotency ----------

    @Test
    void aRetryWithTheSameKeyReturnsTheOriginalOrder() throws Exception {
        UUID customer = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        String json = order(item(PHONE, 1));

        MvcResult first = post(customer, key, json);
        MvcResult retry = post(customer, key, json);

        assertEquals(201, first.getResponse().getStatus());
        assertEquals(201, retry.getResponse().getStatus());
        assertEquals((Object) read(first, "$.data.orderId"), read(retry, "$.data.orderId"));
        assertEquals((Object) read(first, "$.data.orderNumber"), read(retry, "$.data.orderNumber"));
        assertNull(first.getResponse().getHeader("Idempotent-Replayed"));
        assertEquals("true", retry.getResponse().getHeader("Idempotent-Replayed"));
        assertEquals(1, orderCount(customer));
    }

    @Test
    void theSameKeyWithADifferentRequestIsRejected() throws Exception {
        UUID customer = UUID.randomUUID();
        String key = UUID.randomUUID().toString();

        post(customer, key, order(item(PHONE, 1)));
        MvcResult reused = post(customer, key, order(item(PHONE, 2)));

        assertEquals(422, reused.getResponse().getStatus());
        assertEquals("IDEMPOTENCY_KEY_REUSED", read(reused, "$.error.code"));
        assertEquals(1, orderCount(customer));
    }

    @Test
    void differentCustomersMayUseTheSameKey() throws Exception {
        String key = UUID.randomUUID().toString();
        String json = order(item(PHONE, 1));

        MvcResult one = post(UUID.randomUUID(), key, json);
        MvcResult two = post(UUID.randomUUID(), key, json);

        assertEquals(201, one.getResponse().getStatus());
        assertEquals(201, two.getResponse().getStatus());
        assertNotEquals((Object) read(one, "$.data.orderId"), read(two, "$.data.orderId"));
    }

    @Test
    void aFailedRequestDoesNotConsumeTheKey() throws Exception {
        UUID customer = UUID.randomUUID();
        String key = UUID.randomUUID().toString();

        MvcResult rejected = post(customer, key, order(item(DISCONTINUED, 1)));
        MvcResult corrected = post(customer, key, order(item(PHONE, 1)));

        assertEquals(422, rejected.getResponse().getStatus());
        assertEquals(201, corrected.getResponse().getStatus());
        assertNull(corrected.getResponse().getHeader("Idempotent-Replayed"));
    }

    @Test
    void theIdempotencyKeyIsRequired() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/orders")
                .header("X-Customer-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(order(item(PHONE, 1)))).andReturn();

        assertEquals(400, result.getResponse().getStatus());
        assertEquals("IDEMPOTENCY_KEY_REQUIRED", read(result, "$.error.code"));
    }

    @Test
    void anOverlongIdempotencyKeyIsRejected() throws Exception {
        MvcResult result = post(UUID.randomUUID(), "k".repeat(101), order(item(PHONE, 1)));

        assertEquals(400, result.getResponse().getStatus());
        assertEquals("VALIDATION_FAILED", read(result, "$.error.code"));
    }

    @Test
    void concurrentRetriesWithTheSameKeyCreateExactlyOneOrder() throws Exception {
        UUID customer = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        String json = order(item(PHONE, 1));
        int attempts = 6;

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                futures.add(pool.submit(() -> {
                    start.await();          // release all requests at the same moment
                    return post(customer, key, json);
                }));
            }
            start.countDown();

            Set<String> orderIds = new HashSet<>();
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get(30, TimeUnit.SECONDS);
                assertEquals(201, result.getResponse().getStatus());
                orderIds.add(read(result, "$.data.orderId"));
            }
            assertEquals(1, orderIds.size(), "every retry must see the same order");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, orderCount(customer));
    }

    // ---------- read ----------

    @Test
    void ownerCanReadTheOrderWithItemsAndHistory() throws Exception {
        UUID customer = UUID.randomUUID();
        String orderId = read(post(customer, order(item(PHONE, 2), item(HEADPHONES, 1))), "$.data.orderId");

        MvcResult result = get(customer, "/api/v1/orders/" + orderId);

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(orderId, read(result, "$.data.id"));
        assertEquals(customer.toString(), read(result, "$.data.customerId"));
        List<Object> items = read(result, "$.data.items");
        assertEquals(2, items.size());
        assertEquals("CREATED", read(result, "$.data.history[0].to"));
        assertNotNull(read(result, "$.data.createdAt"));
    }

    @Test
    void anotherCustomerSeesNotFound() throws Exception {
        String orderId = read(post(UUID.randomUUID(), order(item(PHONE, 1))), "$.data.orderId");

        MvcResult result = get(UUID.randomUUID(), "/api/v1/orders/" + orderId);

        assertEquals(404, result.getResponse().getStatus());
        assertEquals("NOT_FOUND", read(result, "$.error.code"));
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        MvcResult result = get(UUID.randomUUID(), "/api/v1/orders/" + UUID.randomUUID());

        assertEquals(404, result.getResponse().getStatus());
    }

    @Test
    void malformedOrderIdIsABadRequest() throws Exception {
        MvcResult result = get(UUID.randomUUID(), "/api/v1/orders/not-a-uuid");

        assertEquals(400, result.getResponse().getStatus());
        assertEquals("VALIDATION_FAILED", read(result, "$.error.code"));
    }

    // ---------- helpers ----------

    private static String item(String productId, int quantity) {
        return "{\"productId\":\"%s\",\"quantity\":%d}".formatted(productId, quantity);
    }

    private static String order(String... items) {
        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    /** A request with a fresh Idempotency-Key, like a brand-new client action. */
    private MvcResult post(UUID customerId, String json) throws Exception {
        return post(customerId, UUID.randomUUID().toString(), json);
    }

    private MvcResult post(UUID customerId, String idempotencyKey, String json) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/orders")
                .header("X-Customer-Id", customerId.toString())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)).andReturn();
    }

    private MvcResult get(UUID customerId, String path) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(path)
                .header("X-Customer-Id", customerId.toString())).andReturn();
    }

    private int orderCount(UUID customerId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders WHERE customer_id = ?", Integer.class, customerId);
        return count == null ? 0 : count;
    }

    private static <T> T read(MvcResult result, String jsonPath) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), jsonPath);
    }
}
