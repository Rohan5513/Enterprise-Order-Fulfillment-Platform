# 09 — Order Workflow (Saga)

## 1. Purpose

This document defines how an order moves through inventory reservation and payment when each service has its own database, and what happens when a step fails.

Distributed transactions are avoided (see 03 §4.7). Instead, the workflow is a **saga**: a sequence of local transactions, each of which publishes an event, with **compensating actions** that undo earlier steps when a later step fails.

---

## 2. Approach: Order Service as Orchestrator

The Order Service owns the workflow. It decides what happens next. Inventory and Payment each do one job and report the outcome.

### Why orchestration (and not services reacting to each other)

* One service owns the order status, so there is no ambiguity about who sets `CONFIRMED` or `CANCELLED`
* The order of steps is explicit: stock is reserved **before** money is taken
* Compensation logic lives in one place
* "Why is order X stuck?" is answered by one row and its status history

Kafka remains the transport. Non-critical consumers (Notification) only listen and do not take part in the saga.

---

## 3. Order Statuses

| Status          | Meaning                                                              |
| --------------- | -------------------------------------------------------------------- |
| CREATED         | Order saved. Stock not yet reserved.                                 |
| PAYMENT_PENDING | Stock reserved. Waiting for the payment result.                      |
| CONFIRMED       | Payment succeeded.                                                   |
| CANCELLED       | Order ended without success. Reason stored in `cancellation_reason`. |

Cancellation reasons: `OUT_OF_STOCK`, `PAYMENT_FAILED`, `TIMEOUT`, `CUSTOMER_REQUEST`.

Fulfillment statuses (`PACKED`, `SHIPPED`, `DELIVERED`) will be added later by a Fulfillment Service (see section 12).

---

## 4. Allowed Transitions

| From                     | Trigger                       | To              | Event published (by Order Service) |
| ------------------------ | ----------------------------- | --------------- | ---------------------------------- |
| (none)                   | `POST /orders`                | CREATED         | `OrderCreated`                     |
| CREATED                  | `InventoryReserved`           | PAYMENT_PENDING | `PaymentRequested`                 |
| CREATED                  | `InventoryReservationFailed`  | CANCELLED       | `OrderCancelled` (OUT_OF_STOCK)    |
| PAYMENT_PENDING          | `PaymentSucceeded`            | CONFIRMED       | `OrderConfirmed`                   |
| PAYMENT_PENDING          | `PaymentFailed`               | CANCELLED       | `OrderCancelled` (PAYMENT_FAILED)  |
| CREATED, PAYMENT_PENDING | Timeout sweeper (section 8)   | CANCELLED       | `OrderCancelled` (TIMEOUT)         |
| CREATED, PAYMENT_PENDING | Customer cancels              | CANCELLED       | `OrderCancelled` (CUSTOMER_REQUEST)|

Any transition not in this table is rejected and logged. Cancelling a `CONFIRMED` order is out of scope for now (it needs a refund/return flow).

---

## 5. Happy Path

```text
Order Service            Inventory Service          Payment Service
     | OrderCreated ------------>|                         |
     |<------------ InventoryReserved                      |
     | PaymentRequested ---------------------------------->|
     |<------------------------------------ PaymentSucceeded
     | OrderConfirmed ---------->|                         |
```

1. Order Service saves the order (`CREATED`) and writes `OrderCreated` to its outbox, in one transaction.
2. Inventory consumes `OrderCreated`, reserves stock for **all** items in one local transaction (all-or-nothing), and emits `InventoryReserved`.
3. Order consumes `InventoryReserved`, sets `PAYMENT_PENDING`, and writes `PaymentRequested` to its outbox.
4. Payment consumes `PaymentRequested`, charges via the mock provider, and emits `PaymentSucceeded`.
5. Order consumes `PaymentSucceeded`, sets `CONFIRMED`, and writes `OrderConfirmed`.
6. Inventory consumes `OrderConfirmed` and converts the reservation into a permanent stock deduction.

Every status change, its history row, and its outbox event are written in **one** local transaction.

---

## 6. Compensation (Undo Steps)

| Situation                                        | Compensation                                                                                          |
| ------------------------------------------------ | ----------------------------------------------------------------------------------------------------- |
| Inventory cannot reserve                         | Nothing to undo. No payment was ever requested. Order is cancelled.                                   |
| Payment fails                                    | Order publishes `OrderCancelled`. Inventory releases the reservation.                                 |
| Order cancelled (any reason) with active stock   | Inventory consumes `OrderCancelled` and releases all `ACTIVE` reservations for that order (no-op if none). |
| `PaymentSucceeded` arrives for a `CANCELLED` order (late payment) | Order publishes `RefundRequested`. Payment refunds and emits `PaymentRefunded`.      |

Releasing and refunding must both be safe to run twice (see 10).

---

## 7. Handler Rules

Every event handler in every service must:

1. Be idempotent: record `eventId` in `processed_events` in the same transaction (see 10)
2. Check the current state before acting (state guard). Duplicate or out-of-order events on a terminal status are ignored and logged
3. Use optimistic locking (`version` column on `orders`) so two handlers cannot overwrite each other
4. Write the status change, the `order_status_history` row, and the outbox event in one transaction
5. Never call another service synchronously

---

## 8. Timeouts

A scheduled sweeper in the Order Service cancels stuck orders:

| Status          | Cancel after (configurable) | Reason  |
| --------------- | --------------------------- | ------- |
| CREATED         | 5 minutes                   | TIMEOUT |
| PAYMENT_PENDING | 15 minutes                  | TIMEOUT |

* Runs every minute
* Selects candidates with `FOR UPDATE SKIP LOCKED` so multiple instances never process the same order
* Uses the index on `(order_status, updated_at)`

---

## 9. Failure Scenarios

| Scenario                                          | Behaviour                                                                                   |
| ------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| Inventory Service is down                         | `OrderCreated` waits in Kafka. Order stays `CREATED` until consumed or the sweeper cancels it |
| Duplicate event delivered                         | Skipped via `processed_events`                                                              |
| Order Service crashes after DB commit, before publish | Outbox relay publishes after restart (see 08)                                           |
| Consumer crashes mid-processing                   | Transaction rolled back, Kafka redelivers, handler runs again safely                        |
| Payment times out at provider but actually succeeded | Payment Service reconciles and later emits `PaymentSucceeded`. If the order is already `CANCELLED`, the late-payment rule (section 6) triggers a refund |
| Events arrive out of order                        | State guard ignores events that do not fit the current status                               |

---

## 10. Correlation and Observability

* `correlationId` = `orderId` on every event and every log line
* Log every transition: `orderId, from, to, trigger`
* Metrics: orders by status, time spent per status, saga duration, timeout count, refund count, DLT count

---

## 11. Testing

* Unit-test the transition table (every allowed and every forbidden transition)
* One integration test per failure scenario in section 9
* Concurrency test on inventory: 100 parallel orders against 10 units of stock

---

## 12. Future

* Fulfillment Service: `CONFIRMED -> PACKED -> SHIPPED -> DELIVERED`
* Cancellation after confirmation (refund and restock)
* Partial fulfilment and returns
* Move the saga to a workflow engine (e.g. Temporal) if flows become long-running
