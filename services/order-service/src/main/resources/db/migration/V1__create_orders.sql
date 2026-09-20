CREATE SEQUENCE order_number_seq START 1;

CREATE TABLE orders (
    id                  UUID PRIMARY KEY,
    order_number        VARCHAR(30)   NOT NULL,
    customer_id         UUID          NOT NULL,
    order_status        VARCHAR(20)   NOT NULL,
    cancellation_reason VARCHAR(30),
    total_amount        NUMERIC(12,2) NOT NULL,
    currency_code       CHAR(3)       NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_order_number UNIQUE (order_number),
    CONSTRAINT ck_orders_status CHECK (order_status IN
        ('CREATED', 'PAYMENT_PENDING', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_orders_reason CHECK (cancellation_reason IS NULL OR cancellation_reason IN
        ('OUT_OF_STOCK', 'PAYMENT_FAILED', 'TIMEOUT', 'CUSTOMER_REQUEST')),
    CONSTRAINT ck_orders_cancelled_reason
        CHECK ((order_status = 'CANCELLED') = (cancellation_reason IS NOT NULL)),
    CONSTRAINT ck_orders_total CHECK (total_amount >= 0)
);

CREATE INDEX idx_orders_customer_created ON orders (customer_id, created_at DESC);

-- Used by the timeout sweeper (docs/09 section 8); only indexes rows that can still time out
CREATE INDEX idx_orders_stuck ON orders (order_status, updated_at)
    WHERE order_status IN ('CREATED', 'PAYMENT_PENDING');
