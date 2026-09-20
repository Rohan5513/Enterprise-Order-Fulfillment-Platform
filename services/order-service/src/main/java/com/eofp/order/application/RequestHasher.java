package com.eofp.order.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fingerprint of an order request, used to tell "the same request retried" from "the same key
 * reused for something else". Built from the parsed items rather than the raw JSON, so spacing and
 * property order in the request do not matter. Item order does (docs/10 section 2.2).
 */
final class RequestHasher {

    private RequestHasher() {
    }

    static String hash(List<RequestedItem> items) {
        String canonical = items.stream()
                .map(item -> item.productId() + ":" + item.quantity())
                .collect(Collectors.joining("|"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);   // 64 hex characters
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available on the JVM", e);
        }
    }
}
