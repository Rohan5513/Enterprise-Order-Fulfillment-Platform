CREATE TABLE order_status_history (
    id            UUID PRIMARY KEY,
    order_id      UUID        NOT NULL,
    from_status   VARCHAR(20),
    to_status     VARCHAR(20) NOT NULL,
    trigger_event VARCHAR(50) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX idx_order_status_history_order ON order_status_history (order_id, created_at);
