package com.eofp.order.api;

import java.time.Instant;

/** Success envelope from docs/06 section 1.1. */
public record ApiResponse<T>(T data, String message, Instant timestamp) {

    public static <T> ApiResponse<T> of(T data, String message) {
        return new ApiResponse<>(data, message, Instant.now());
    }
}
