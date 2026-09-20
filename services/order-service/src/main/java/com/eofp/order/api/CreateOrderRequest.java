package com.eofp.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * There is deliberately no customerId, currency or price: identity comes from the caller's
 * credentials and prices come from the product catalog (docs/06 section 5.1).
 */
public record CreateOrderRequest(
        @NotEmpty @Size(max = 50) List<@NotNull @Valid Item> items) {

    public record Item(
            @NotNull UUID productId,
            @NotNull @Min(1) @Max(100) Integer quantity) {
    }
}
