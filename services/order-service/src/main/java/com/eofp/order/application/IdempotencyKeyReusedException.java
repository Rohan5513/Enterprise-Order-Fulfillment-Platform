package com.eofp.order.application;

/** The same Idempotency-Key was sent with a different request body. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("This Idempotency-Key was already used with a different request");
    }
}
