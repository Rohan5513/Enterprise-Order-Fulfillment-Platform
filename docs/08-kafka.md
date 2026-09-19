# 08 — Kafka & Event Design

## 1. Overview

Kafka is used for asynchronous communication between services.

It enables:

* Loose coupling between services
* Scalable event processing
* Reliable communication in distributed systems

---

## 2. Topic Design

### Topics

* `order-events`
* `inventory-events`
* `payment-events`

Each topic represents a business domain and carries related events.

---

## 3. Event Naming Convention

Events use **past-tense naming** to represent completed actions:

* OrderCreated
* InventoryReserved
* InventoryReleased
* PaymentProcessed
* PaymentFailed

---

## 4. Event Structure

All events follow a consistent structure:

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "timestamp": "ISO-8601",
  "data": {}
}
```

---

## 5. OrderCreated Event

### Example Payload

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "timestamp": "2026-01-01T10:00:00Z",
  "data": {
    "orderId": "uuid",
    "customerId": "uuid",
    "items": [
      {
        "productId": "uuid",
        "quantity": 2
      }
    ],
    "totalAmount": 300.00,
    "currencyCode": "INR"
  }
}
```

---

## 6. Producer Responsibilities

### Order Service

* Publishes `OrderCreated` event
* Ensures event is emitted only after successful order creation
* Uses Outbox pattern for reliability (defined later)

---

## 7. Consumer Responsibilities

### Inventory Service

* Consumes `OrderCreated` event
* Reserves stock
* Emits:

  * `InventoryReserved`
  * `InventoryFailed`

---

### Payment Service

* Consumes `OrderCreated` event
* Processes payment
* Emits:

  * `PaymentProcessed`
  * `PaymentFailed`

---

## 8. Idempotency in Event Processing

Consumers must handle duplicate events safely.

### Approach

* Track processed `eventId`
* Ignore duplicate processing

This ensures safe retries and prevents duplicate side effects.

---

## 9. Failure Handling (High-Level)

* Retry transient failures
* Log and track failed events
* Introduce dead-letter queues (future enhancement)

---

## 10. Event Design Principles

* Events are immutable
* Payload should contain only required data
* Avoid tight coupling between services
* Maintain backward compatibility when evolving schema

---

## 11. Reliable Event Publishing (Outbox Pattern)

### 11.1 Problem Statement

Directly publishing events after database operations can lead to inconsistencies:

* Database commit succeeds but event publish fails
* Event publish succeeds but database commit fails

---

### 11.2 Solution Overview

The Outbox Pattern ensures reliable event publishing by storing events in the database within the same transaction as business data.

---

### 11.3 Flow

1. Order is created
2. Event is inserted into `outbox_events` table in the same transaction
3. Transaction is committed
4. Background process reads pending events
5. Events are published to Kafka
6. Events are marked as processed

---

### 11.4 Outbox Table Design

```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at_utc TIMESTAMP NOT NULL,
    processed_at_utc TIMESTAMP
);
```

---

### 11.5 Status Values

* PENDING
* PROCESSED
* FAILED

---

### 11.6 Background Processing

* Poll events where `status = PENDING`
* Publish to Kafka
* Update status to `PROCESSED`

---

### 11.7 Retry Strategy

* Failed events can be retried
* Prevents data loss
* Ensures eventual consistency

---

### 11.8 Design Benefits

* Guarantees consistency between DB and events
* Prevents event loss
* Enables retry mechanisms
* Decouples business logic from event publishing

---

### 11.9 Design Considerations

* Consumers must be idempotent
* Monitor failed events
* Avoid duplicate publishing

---

## 12. Future Enhancements

* Schema registry (Avro/JSON schema)
* Partitioning strategy
* Consumer groups
* Dead-letter queues (DLQ)
* Event versioning strategy

---
