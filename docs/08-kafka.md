# 08 — Kafka & Event Design

## 1. Overview

Kafka carries asynchronous events between services. It provides:

* Loose coupling between services
* Durable, replayable event history
* Independent scaling of consumers

Delivery is **at-least-once**. Every consumer must be idempotent (see 10).

---

## 2. Topics and Keys

| Topic              | Carries events about | Dead-letter topic      |
| ------------------ | -------------------- | ---------------------- |
| `order-events`     | Order lifecycle      | `order-events.DLT`     |
| `inventory-events` | Stock reservations   | `inventory-events.DLT` |
| `payment-events`   | Payments and refunds | `payment-events.DLT`   |

* **Message key = `orderId`** for every event. All events for one order go to the same partition, so consumers see them in order
* Partitions: 3 (local/dev). Replication factor: 1 in dev, 3 in production
* Retention: 7 days
* One consumer group per service (`inventory-service`, `payment-service`, and so on)

---

## 3. Event Naming

Past tense, describing something that already happened:

`OrderCreated`, `InventoryReserved`, `InventoryReservationFailed`, `PaymentSucceeded`, `PaymentFailed`

Events the Order Service publishes to ask another service to act use the form `<Thing>Requested` (`PaymentRequested`, `RefundRequested`): the fact is that the request was made.

---

## 4. Event Envelope

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "eventVersion": 1,
  "occurredAt": "2026-01-01T10:00:00Z",
  "aggregateId": "order-uuid",
  "correlationId": "order-uuid",
  "data": {}
}
```

* `eventId` is unique and is the key for consumer deduplication
* `correlationId` is the `orderId`, so one order can be traced across all services
* Trace context (`traceparent`) travels in Kafka headers

---

## 5. Event Catalogue

| Event                        | Topic              | Producer  | Consumers              | Meaning                                         |
| ---------------------------- | ------------------ | --------- | ---------------------- | ----------------------------------------------- |
| `OrderCreated`               | `order-events`     | Order     | Inventory, Notification| Order saved. Reserve stock                      |
| `InventoryReserved`          | `inventory-events` | Inventory | Order                  | All items reserved                              |
| `InventoryReservationFailed` | `inventory-events` | Inventory | Order                  | Could not reserve. Nothing was reserved         |
| `InventoryReleased`          | `inventory-events` | Inventory | (audit)                | Reservations released                           |
| `PaymentRequested`           | `order-events`     | Order     | Payment                | Charge this amount                              |
| `PaymentSucceeded`           | `payment-events`   | Payment   | Order                  | Payment completed                               |
| `PaymentFailed`              | `payment-events`   | Payment   | Order                  | Payment declined or failed                      |
| `OrderConfirmed`             | `order-events`     | Order     | Inventory, Notification| Commit stock. Notify customer                   |
| `OrderCancelled`             | `order-events`     | Order     | Inventory, Notification| Release stock. Notify customer                  |
| `RefundRequested`            | `order-events`     | Order     | Payment                | Late payment on a cancelled order               |
| `PaymentRefunded`            | `payment-events`   | Payment   | Notification           | Refund completed                                |

Inventory reservation is all-or-nothing per order, which is why there is a single `InventoryReservationFailed`.

---

## 6. Example Payloads

### OrderCreated

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "eventVersion": 1,
  "occurredAt": "2026-01-01T10:00:00Z",
  "aggregateId": "order-uuid",
  "correlationId": "order-uuid",
  "data": {
    "orderId": "order-uuid",
    "customerId": "customer-uuid",
    "items": [
      { "productId": "product-uuid", "quantity": 2 }
    ],
    "totalAmount": 300.00,
    "currencyCode": "INR"
  }
}
```

### PaymentRequested

```json
{
  "data": {
    "orderId": "order-uuid",
    "amount": 300.00,
    "currencyCode": "INR"
  }
}
```

### PaymentSucceeded

```json
{
  "data": {
    "orderId": "order-uuid",
    "paymentId": "payment-uuid",
    "paymentReference": "gateway-ref",
    "amount": 300.00,
    "currencyCode": "INR"
  }
}
```

### OrderCancelled

```json
{
  "data": {
    "orderId": "order-uuid",
    "reason": "PAYMENT_FAILED"
  }
}
```

(Each example shows the `data` block. The envelope from section 4 wraps all of them.)

---

## 7. Reliable Publishing (Outbox Pattern)

### 7.1 Problem

A service must update its database **and** publish an event. These two cannot be one atomic operation:

* DB commit succeeds, publish fails -> event lost, workflow stalls
* Publish succeeds, DB commit fails -> event describes something that never happened

### 7.2 Solution

Write the event to an `outbox_events` table **in the same transaction** as the business change. A separate relay publishes it afterwards.

**Every service that publishes events has its own outbox** (Order, Inventory, Payment).

### 7.3 Table

```sql
CREATE TABLE outbox_events (
    id               UUID PRIMARY KEY,
    aggregate_type   VARCHAR(50)  NOT NULL,
    aggregate_id     UUID         NOT NULL,
    event_type       VARCHAR(100) NOT NULL,
    topic            VARCHAR(100) NOT NULL,
    event_key        VARCHAR(100) NOT NULL,
    payload          JSONB        NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count      INT          NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error       TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at     TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending
    ON outbox_events (next_attempt_at)
    WHERE status = 'PENDING';
```

* `id` **is** the `eventId` inside the payload
* `payload` holds the full envelope
* Status values: `PENDING`, `PUBLISHED`, `FAILED`

### 7.4 Relay

Runs every second or so:

```sql
SELECT * FROM outbox_events
WHERE status = 'PENDING' AND next_attempt_at <= now()
ORDER BY created_at
LIMIT 100
FOR UPDATE SKIP LOCKED;
```

1. Publish each row to `topic` with key `event_key`. Producer settings: `acks=all`, `enable.idempotence=true`
2. On success: `status = PUBLISHED`, set `published_at`
3. On failure: `retry_count + 1`, `next_attempt_at = now() + backoff` (2^n seconds, capped at 5 minutes), store `last_error`
4. After 10 failures: `status = FAILED`, raise a metric and alert. Replay manually by setting the status back to `PENDING`
5. Delete `PUBLISHED` rows older than 7 days

`SKIP LOCKED` lets several relay instances run without publishing the same row twice.

### 7.5 Guarantees

* No event is lost: it is committed with the business data
* An event may be published **more than once** (published, then crash before marking). Consumers deduplicate
* The saga (09) is causally ordered: an event for an order is only created after the previous one was consumed, so keyed partitions preserve the correct order

---

## 8. Consumer Rules

Per event, in **one** DB transaction: dedupe (`processed_events`), apply the change, write outbox events. Then commit the Kafka offset.

### Retries and dead-letter topics

* Transient errors (DB down, timeout): retry with exponential backoff (for example 1s, 2s, 4s, 8s, 16s)
* Permanent errors (unparseable message, invalid state transition): send straight to `<topic>.DLT`
* Retries exhausted: send to `<topic>.DLT`
* Use an error-handling deserializer so one bad message cannot block the partition
* Alert on any message reaching a DLT. Build a replay tool later

---

## 9. Event Design Principles

* Events are immutable facts
* Carry IDs and the data consumers need, not whole entities
* Include what the consumer needs so it never has to call back (for example, `PaymentRequested` carries the amount)
* Evolve schemas by **adding optional fields only**. Consumers ignore unknown fields
* A breaking change requires a new `eventVersion`, with both versions published during migration

---

## 10. Future Enhancements

* Schema registry (Avro or JSON Schema) and contract tests
* Change Data Capture (Debezium) instead of polling the outbox
* Replay tooling for dead-letter topics
* Consumer lag monitoring and alerts
