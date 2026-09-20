package com.eofp.order.api;

import com.eofp.order.application.CreateOrderResult;
import com.eofp.order.application.CreateOrderService;
import com.eofp.order.application.OrderQueryService;
import com.eofp.order.application.RequestedItem;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * SECURITY NOTE (temporary): the caller's identity is read from the X-Customer-Id header because
 * there is no authentication yet. That is trivially forgeable. In M6 the customer ID comes from
 * the validated JWT (docs/06 section 2) and this header is removed. Nothing else in the code
 * knows where the ID comes from, so that change is confined to this class.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderService createOrderService;
    private final OrderQueryService orderQueryService;

    public OrderController(CreateOrderService createOrderService, OrderQueryService orderQueryService) {
        this.createOrderService = createOrderService;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateOrderResponse>> create(
            @RequestHeader("X-Customer-Id") UUID customerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        List<RequestedItem> items = request.items().stream()
                .map(item -> new RequestedItem(item.productId(), item.quantity()))
                .toList();
        CreateOrderResult result = createOrderService.create(customerId, idempotencyKey, items);

        ResponseEntity.BodyBuilder response = ResponseEntity
                .created(URI.create("/api/v1/orders/" + result.order().orderId()));
        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(ApiResponse.of(
                CreateOrderResponse.from(result.order()), "Order created successfully"));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailsResponse> get(
            @RequestHeader("X-Customer-Id") UUID customerId,
            @PathVariable UUID orderId) {

        return ApiResponse.of(
                OrderDetailsResponse.from(orderQueryService.getOrder(orderId, customerId)),
                "Order retrieved successfully");
    }
}
