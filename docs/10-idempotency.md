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
## 7. Order Creation Idempotency Flow

### 7.1 Request Flow

1. Client sends request with `Idempotency-Key`
2. System checks if key exists
3. If not present → process request
4. If present → return stored response

---

### 7.2 Processing Steps

* Insert idempotency record with status = `IN_PROGRESS`
* Process order creation
* Store response payload
* Update status to `COMPLETED`

---

### 7.3 Retry Handling

* If status = `COMPLETED` → return stored response
* If status = `IN_PROGRESS` → reject or retry later
* If status = `FAILED` → allow reprocessing

---

### 7.4 Payload Validation

* Same idempotency key must have identical request payload
* Requests with different payload for same key are rejected

---

### 7.5 Transaction Handling

Idempotency record and order creation must be part of a single transaction to ensure consistency.

---

### 7.6 Failure Recovery

* In case of system crash, retries will reuse stored data
* `resource_id` can be used to fetch existing order

---

### 7.7 Design Benefits

* Prevents duplicate order creation
* Ensures safe retries
* Improves system reliability

---
