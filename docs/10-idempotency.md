# 10 — Idempotency & Reliability Design

## 1. Overview

Idempotency ensures that repeated execution of the same request produces the same result without unintended side effects.

It is critical for handling:

* Network retries
* Duplicate requests
* Distributed system failures

---

## 2. API-Level Idempotency

### 2.1 Approach

For operations like **Create Order**, clients must send an idempotency key.

Example header:

```http
Idempotency-Key: 123e4567-e89b-12d3-a456-426614174000
```

---

### 2.2 Behavior

* First request → processed normally
* Duplicate request with same key → return previous response

---

### 2.3 Storage Strategy

Maintain a table to track processed requests:

```sql
CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(255) NOT NULL,
    response_payload TEXT,
    status VARCHAR(20) NOT NULL,
    created_at_utc TIMESTAMP NOT NULL
);
```

---

### 2.4 Key Rules

* Idempotency key must be unique per request
* Same key + different payload → reject
* Store response for reuse

---

## 3. Consumer-Level Idempotency

When processing events:

* Same event may be delivered multiple times

### Approach:

* Track processed `eventId`
* Skip duplicates

---

### Example Table

```sql
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at_utc TIMESTAMP NOT NULL
);
```

---

## 4. Design Considerations

### Stateless APIs

* Idempotency handled via storage
* No session dependency

---

### Data Consistency

* Prevents duplicate order creation
* Prevents double payment

---

### Failure Handling

* Safe retries without side effects
* Supports eventual consistency

---

## 5. Edge Cases

* Same key with different payload
* Partial processing failures
* Expired idempotency keys (future enhancement)

---

## 6. Future Enhancements

* TTL for idempotency keys
* Distributed cache (Redis) for faster lookup
* Integration with message queues

---
