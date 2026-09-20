package com.eofp.order.infrastructure.catalog;

import com.eofp.order.application.ProductCatalog;
import com.eofp.order.application.ProductInfo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TEMPORARY. Stands in for the Product Service until it exists (M6). When the real HTTP client
 * is written it implements the same ProductCatalog port and this class is deleted.
 */
@Component
public class StubProductCatalog implements ProductCatalog {

    private static final Map<UUID, ProductInfo> PRODUCTS = Map.of(
            id("11111111-1111-1111-1111-111111111111"),
            new ProductInfo(id("11111111-1111-1111-1111-111111111111"), new BigDecimal("15000.00"), "INR", true),
            id("22222222-2222-2222-2222-222222222222"),
            new ProductInfo(id("22222222-2222-2222-2222-222222222222"), new BigDecimal("2500.00"), "INR", true),
            id("33333333-3333-3333-3333-333333333333"),
            new ProductInfo(id("33333333-3333-3333-3333-333333333333"), new BigDecimal("999.00"), "INR", false),
            id("44444444-4444-4444-4444-444444444444"),
            new ProductInfo(id("44444444-4444-4444-4444-444444444444"), new BigDecimal("10.00"), "USD", true));

    @Override
    public Map<UUID, ProductInfo> findByIds(Collection<UUID> productIds) {
        Map<UUID, ProductInfo> found = new HashMap<>();
        for (UUID productId : productIds) {
            ProductInfo product = PRODUCTS.get(productId);
            if (product != null) {
                found.put(productId, product);
            }
        }
        return found;
    }

    private static UUID id(String value) {
        return UUID.fromString(value);
    }
}
