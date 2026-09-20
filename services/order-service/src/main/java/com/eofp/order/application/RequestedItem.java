package com.eofp.order.application;

import java.util.UUID;

public record RequestedItem(UUID productId, int quantity) {
}
