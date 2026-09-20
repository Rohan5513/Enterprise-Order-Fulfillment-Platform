CREATE TABLE order_items (
    id          UUID PRIMARY KEY,
    order_id    UUID          NOT NULL,
    product_id  UUID          NOT NULL,
    quantity    INT           NOT NULL,
    unit_price  NUMERIC(12,2) NOT NULL,
    total_price NUMERIC(12,2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT uq_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_total CHECK (total_price = unit_price * quantity)
);
