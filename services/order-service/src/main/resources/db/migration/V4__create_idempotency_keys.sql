CREATE TABLE idempotency_keys (
    id              UUID PRIMARY KEY,
    customer_id     UUID         NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    resource_id     UUID         NOT NULL,
    response_status SMALLINT,
    response_body   JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    -- The database is the referee: two concurrent requests with the same key cannot both insert
    CONSTRAINT uq_idempotency_scope UNIQUE (customer_id, idempotency_key)
);

-- Used by the cleanup job that deletes expired keys
CREATE INDEX idx_idempotency_expires_at ON idempotency_keys (expires_at);
