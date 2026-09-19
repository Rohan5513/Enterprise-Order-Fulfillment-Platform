# 10 — Idempotency & Reliability Design

## 1. Overview

Idempotency means that repeating the same operation produces the same result without extra side effects.

It is required at three levels:

| Level    | Threat                                    | Mechanism                              |
| -------- | ----------------------------------------- | -------------------------------------- |
| API      | Client retries, double-clicks             | `Idempotency-Key` header + table       |
| Consumer | Kafka delivers an event more than once    | `processed_events` table               |
| Business | Same command reaches a service twice      | Unique constraints on business keys    |

---

## 2. API-Level Idempotency (Create Order)

### 2.1 Client contract

```http
POST /api/v1/orders
Idempotency-Key: 123e4567-e89b-12d3-a456-426614174000
```

* Required on `POST /api/v1/orders`. Missing key -> `400 IDEMPOTENCY_KEY_REQUIRED`
* UUID v4 recommended, maximum 100 characters
* One key per **user intention**. The client reuses the same key for every retry of that intention
* Scope is per customer: the customer comes from the JWT, so two customers can never collide

### 2.2 Table

```sql
CREATE TABLE idempotency_keys (
    id               UUID PRIMARY KEY,
    customer_id      UUID         NOT NULL,
    idempotency_key  VARCHAR(100) NOT NULL,
    request_hash     CHAR(64)     NOT NULL,
    resource_id      UUID         NOT NULL,
    response_status  SMALLINT,
    response_body    JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_idempotency_scope UNIQUE (customer_id, idempotency_key)
);

CREATE INDEX idx_idempotency_expires_at ON idempotency_keys (expires_at);
```

* `request_hash`: SHA-256 (hex) of the canonical request body (sorted keys; item order is significant)
* `resource_id`: the order ID, generated before the insert
* `response_*`: filled in the same transaction, so other requests never see them empty

### 2.3 Processing flow

1. **Before any transaction:** validate input, take the customer from the JWT, compute `request_hash`, and call the Product Service for prices. No DB transaction is open during remote calls
2. Begin transaction
3. Insert the key row with `ON CONFLICT (customer_id, idempotency_key) DO NOTHING`
4. **Row inserted (first time):** create the order, items, status history and outbox event; store the response on the key row; commit; return `201`
5. **No row inserted (key exists):** read the existing row
   * same `request_hash` -> return the stored status and body (add header `Idempotent-Replayed: true`)
   * different `request_hash` -> `422 IDEMPOTENCY_KEY_REUSED`

### 2.4 Concurrent duplicates

If two identical requests arrive together, the second insert **waits** on the unique index until the first transaction ends:

* first commits -> second sees the row and replays the stored response
* first rolls back -> second proceeds as a first-time request

This is why no `IN_PROGRESS` status is needed.

### 2.5 What is (not) stored

* Only **successful** creations are stored
* Validation errors and rolled-back attempts leave no row, so the client can fix the request and retry with the same key
* If the server crashes after commit but before responding, the retry receives the stored response

### 2.6 Retention

Keys expire after 24 hours. A scheduled job deletes expired rows. After expiry, the same key is treated as a new request.

---

## 3. Consumer-Level Idempotency

Kafka guarantees at-least-once delivery, so every consumer must tolerate duplicates.

```sql
CREATE TABLE processed_events (
    event_id      UUID         NOT NULL,
    handler       VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, handler)
);
```

Rules:

1. In **one** DB transaction: insert into `processed_events` (`ON CONFLICT DO NOTHING`), apply the business change, write outbox events
2. If the insert added no row, the event was already handled: skip it
3. Commit the Kafka offset only **after** the DB transaction commits
4. Keep rows longer than the Kafka retention period (for example 14 days), then purge

At-least-once delivery plus this dedup gives effectively-once processing.

---

## 4. Business-Level Idempotency

Unique constraints protect against duplicates that slip past the layers above:

| Service   | Constraint                                    | Effect                                                 |
| --------- | --------------------------------------------- | ------------------------------------------------------ |
| Inventory | `UNIQUE (order_id, product_id)` on reservations | Reserving twice is a no-op                           |
| Inventory | Release only touches `ACTIVE` reservations    | Releasing twice is a no-op                             |
| Payment   | `UNIQUE (order_id, attempt_no)`               | One row per attempt                                    |
| Payment   | Provider call uses key `<order_id>:<attempt_no>` | Provider never charges twice for the same attempt   |
| Order     | Transition table + state guard (see 09)       | Re-applied transitions are ignored                     |

---

## 5. Edge Cases

* Same key, different payload -> `422`
* Same key, same payload, concurrent -> second waits, then replays
* Crash before commit -> nothing saved, safe to retry
* Crash after commit, before response -> retry returns stored response
* Key expired -> treated as a new request
* Same key used by two customers -> independent

---

## 6. Future Enhancements

* Redis cache in front of the key lookup
* Store selected failure responses (for example, 409 conflicts)
* Metrics: replay rate, key-reuse rejections
