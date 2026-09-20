package com.eofp.order.application;

/** A well-formed order request that breaks a business rule. The API layer maps the reason to HTTP. */
public class OrderRequestRejectedException extends RuntimeException {

    public enum Reason {
        DUPLICATE_PRODUCT,
        PRODUCT_NOT_AVAILABLE,
        MIXED_CURRENCY
    }

    private final Reason reason;

    public OrderRequestRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
