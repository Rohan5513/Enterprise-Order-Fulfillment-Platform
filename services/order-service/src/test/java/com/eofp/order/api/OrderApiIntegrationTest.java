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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real HTTP layer, validation, business rules and PostgreSQL together.
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

        List<OrderStatusHistory> history = historyRepository.findByOrderIdOrderByCreatedAtAsc(UUID.fromString(orderId));
        assertEquals(1, history.size());
        assertEquals(OrderStatus.CREATED, history.get(0).getToStatus());
    }

    @Test
    void rejectsARequestWithoutIdentity() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/orders")
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

    private MvcResult post(UUID customerId, String json) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/orders")
                .header("X-Customer-Id", customerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)).andReturn();
    }

    private MvcResult get(UUID customerId, String path) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(path)
                .header("X-Customer-Id", customerId.toString())).andReturn();
    }

    private static <T> T read(MvcResult result, String jsonPath) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), jsonPath);
    }
}
