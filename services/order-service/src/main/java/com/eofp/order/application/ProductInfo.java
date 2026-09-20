package com.eofp.order.application;

import java.math.BigDecimal;
import java.util.UUID;

/** What the Order Service needs to know about a product when an order is placed. */
public record ProductInfo(UUID id, BigDecimal unitPrice, String currencyCode, boolean active) {
}
