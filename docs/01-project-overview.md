# Enterprise Order & Fulfillment Platform (EOFP)

## 1. Project Overview

EOFP is a backend-first, event-driven platform that takes a customer order from placement to confirmation: validating it, reserving stock, taking payment, and notifying the customer, while staying correct when steps fail or messages are duplicated.

It is a portfolio project built to demonstrate production-minded backend engineering: service boundaries, data ownership, reliable messaging, idempotency, resilience, and observability.

Design target: about 1,000 orders per day in a single region, with short spikes of a few orders per second.

---

## 2. Business Problem

An order touches several capabilities that must agree with each other: customers, products, stock, payments, and notifications. Failures are normal, not exceptional:

* Two customers try to buy the last unit at the same moment
* A client retries after a timeout and creates a duplicate order
* Stock is reserved but the payment fails
* A payment succeeds after the order was already cancelled
* A service is down while orders keep arriving

The platform must never oversell, double-charge, lose an order, or leave stock permanently reserved.

---

## 3. Objectives

1. Guarantee correct outcomes under retries, duplicate events, and partial failure
2. Keep clear service boundaries with each service owning its data
3. Make every order's journey traceable and explainable
4. Be operable: observable, testable, and deployable with one command
5. Document every major decision and its trade-offs

---

## 4. Functional Requirements

| ID     | Requirement                                                                      |
| ------ | -------------------------------------------------------------------------------- |
| FR-001 | Customer registration                                                            |
| FR-002 | Customer login (JWT)                                                             |
| FR-003 | View own profile                                                                 |
| FR-004 | Browse and filter products                                                       |
| FR-005 | View product details                                                             |
| FR-006 | Admin creates and updates products                                               |
| FR-007 | Admin manages inventory (restock and adjust)                                     |
| FR-008 | Place an order with multiple items                                               |
| FR-009 | Duplicate-safe order placement (idempotency key)                                 |
| FR-010 | Automatic stock reservation, all-or-nothing per order                            |
| FR-011 | Payment processing through a mock provider (success, failure, timeout scenarios) |
| FR-012 | Automatic order confirmation after successful payment                            |
| FR-013 | Automatic cancellation on out-of-stock, payment failure, or timeout, with stock released |
| FR-014 | View order details, status, and status history                                   |
| FR-015 | Customer cancels an unconfirmed order                                            |
| FR-016 | Order notifications (simulated) for created, confirmed, cancelled, and refunded  |

---

## 5. Non-Functional Requirements

Targets are design goals to be verified in the load test, not guarantees.

| Area           | Requirement                                                                                          |
| -------------- | ---------------------------------------------------------------------------------------------------- |
| Security       | JWT auth at gateway and services; role-based access; ownership checks; hashed passwords; no secrets in the repo |
| API versioning | `/api/v1`; additive changes within a version; breaking changes need a new version                    |
| Validation     | Input validated at the edge; invariants also enforced by database constraints                         |
| Error handling | Uniform error model with codes and trace IDs; no internal details leaked                             |
| Idempotency    | Safe retries at API, consumer, and business level (see 10)                                           |
| Observability  | Structured logs with correlation IDs, metrics per service and per order status, distributed tracing  |
| Resilience     | Timeouts, retries with backoff, circuit breaker on the one sync dependency, dead-letter topics, outbox |
| Testability    | Unit tests on the state machine; integration tests with real PostgreSQL and Kafka (Testcontainers); concurrency and failure-injection tests |
| Performance    | Order creation p95 under 300 ms (excluding async processing) at the design load; verified by load test |
| Correctness    | No oversell; no duplicate charge; every order reaches `CONFIRMED` or `CANCELLED` within the timeout window |

---

## 6. User Roles

| Role     | Can                                                                                     |
| -------- | --------------------------------------------------------------------------------------- |
| CUSTOMER | Register, log in, browse products, place and cancel own orders, view own orders         |
| ADMIN    | Everything a customer can, plus manage products and inventory, and view any order       |

---

## 7. Business Flow

Happy path:

```text
Place order -> CREATED -> stock reserved -> PAYMENT_PENDING -> payment succeeds -> CONFIRMED -> notification
```

Failure paths:

* Not enough stock: `CANCELLED` (`OUT_OF_STOCK`), no payment attempted
* Payment fails: `CANCELLED` (`PAYMENT_FAILED`), stock released
* No progress within the time limit: `CANCELLED` (`TIMEOUT`), stock released
* Customer cancels before confirmation: `CANCELLED` (`CUSTOMER_REQUEST`), stock released
* Payment succeeds after cancellation: refund

Full detail, transitions, and compensation: **09-order-saga.md**.

---

## 8. Architecture

```text
                    Angular SPA
                         |
                   API Gateway
            (routing, JWT validation)
                         |
   +---------+---------+-+-------+-----------+--------------+
   |         |         |         |           |              |
Customer  Product    Order    Inventory   Payment     Notification
Service   Service   Service   Service     Service       Service
   |         |     (orchestrator)  |          |              |
customer_db product_db order_db inventory_db payment_db notification_db

Order, Inventory, Payment, Notification communicate via Kafka:
  order-events | inventory-events | payment-events  (+ .DLT topics)

Cross-cutting: OpenTelemetry -> Prometheus / Grafana, structured logs
```

Synchronous REST is used for client requests and for one internal call (Order to Product for prices). Everything else in the order workflow is asynchronous.

### Technology

| Concern          | Choice                          | Why                                                          |
| ---------------- | ------------------------------- | ------------------------------------------------------------ |
| Backend          | Java (LTS) with Spring Boot     | Common enterprise stack; strong ecosystem                    |
| Database         | PostgreSQL, Flyway migrations   | ACID, partial indexes, `SKIP LOCKED`, `JSONB`                |
| Messaging        | Kafka                           | Durable log, replay, per-key ordering, consumer groups       |
| Frontend         | Angular                         | Customer and admin UI                                        |
| Auth             | JWT                             | Stateless, works across services                             |
| Resilience       | Resilience4j                    | Circuit breaker, retry, timeout                              |
| Observability    | OpenTelemetry, Prometheus, Grafana | Vendor-neutral standards                                  |
| Build and deploy | Docker, Docker Compose, GitHub Actions | One-command local run; CI on every push               |

---

## 9. Key Architecture Decisions

1. **Microservices at this scale.** 1,000 orders/day does not require them. They are chosen deliberately to demonstrate distributed-systems patterns. In a real project I would start as a modular monolith and extract services when team or scaling boundaries demand it
2. **Database per service, enforced by per-service DB users.** Independent evolution; the cost is no cross-service joins and eventual consistency
3. **Saga with an orchestrator (Order Service).** The workflow has an ordering constraint and needs compensation; one owner keeps it explicit and debuggable (09)
4. **Transactional outbox plus idempotent consumers.** Avoids the dual-write problem and gives effectively-once processing on top of at-least-once delivery (08, 10)
5. **Atomic conditional update for stock.** Prevents overselling without long-held locks (03 §6)
6. **Identity from the token, never from the request.** Prevents users acting as other users (06 §2)

---

## 10. Scope and Non-Goals (initial version)

In scope: everything in section 4.

Out of scope for now: real payment gateway, real email or SMS delivery, shipping and fulfillment steps, multi-warehouse inventory, discounts and coupons, tax, returns.

---

## 11. Future Enhancements

* Fulfillment Service: `CONFIRMED -> PACKED -> SHIPPED -> DELIVERED`
* Cancellation and refund of confirmed orders
* Real payment provider and reconciliation job
* Server-sent events for live order status
* Change Data Capture (Debezium) instead of outbox polling
* Schema registry and consumer contract tests
* Kubernetes deployment
