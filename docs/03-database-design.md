# 03 — Database Design

## 1. Database Strategy

We are following a **database-per-service** pattern.

Each microservice owns its own database and is solely responsible for its data.

### Key Principles

* No service can directly access another service's database
* All inter-service communication happens via:

  * REST APIs (synchronous)
  * Events (Kafka, asynchronous)
* Each service can evolve independently

### Why this approach?

* Ensures loose coupling
* Enables independent deployments
* Improves scalability
* Matches real-world microservices architecture

---

## 2. Database Technology

We are using:

* **PostgreSQL** for all services (initial phase)

### Why PostgreSQL?

* Strong ACID guarantees (important for orders/payments)
* Widely used in production systems
* Excellent support for indexing and performance tuning
* Works well with Spring Boot and JPA

---

## 3. Database Ownership per Service

| Service              | Database Name   |
| -------------------- | --------------- |
| Customer Service     | customer_db     |
| Product Service      | product_db      |
| Order Service        | order_db        |
| Inventory Service    | inventory_db    |
| Payment Service      | payment_db      |
| Notification Service | notification_db |

---

## 4. General Design Rules

### 4.1 Primary Keys

* All tables use **UUID** as primary key
* Benefits:

  * Avoids ID collisions across services
  * Safe for distributed systems

---

### 4.2 Audit Fields

All tables include:

* `created_at`
* `updated_at`

Optional (later):

* `created_by`
* `updated_by`

---

### 4.3 Status Fields

* Use ENUM or controlled values
* Avoid free-text statuses

Examples:

* ORDER: CREATED, PENDING_PAYMENT, CONFIRMED, CANCELLED
* PAYMENT: SUCCESS, FAILED, TIMEOUT
* PRODUCT: ACTIVE, INACTIVE

---

### 4.4 Foreign Keys

* Allowed **only within the same service**
* Cross-service relationships use IDs only

Example:

* Order Service stores `customer_id`
* But does NOT enforce foreign key to Customer DB

---

### 4.5 Indexing Strategy

Indexes will be created on:

* Frequently queried fields
* Foreign key references
* Searchable attributes

Examples:

* `email` (Customer)
* `sku` (Product)
* `order_number` (Order)

---

### 4.6 Data Consistency

* Strong consistency within a service
* Eventual consistency across services

---

### 4.7 Transaction Boundaries

* Transactions are limited to a single service
* Distributed transactions are avoided
* Future solution:

  * Saga pattern (to be implemented later)

---

## 5. High-Level Schema Overview

Each service will define its own schema:

### Customer Service

* Customer

### Product Service

* Product

### Order Service

* Order
* OrderItem

### Inventory Service

* Inventory

### Payment Service

* Payment

### Notification Service

* (No persistent storage initially, optional later)

---

## 6. Design Trade-offs

### Advantages

* High scalability
* Service independence
* Clear ownership boundaries

### Trade-offs

* No cross-service joins
* Requires API calls for data aggregation
* Eventual consistency challenges

---

## 7. What Can Go Wrong?

* Data inconsistency between services
* Event failures (Kafka issues later)
* Duplicate operations (requires idempotency)
* Concurrency issues (especially inventory)

---

## 8. Production Considerations

* Database per service may evolve into:

  * Separate DB instances
  * Cloud-managed databases
* Read replicas for scaling
* Backup and recovery strategies
* Monitoring and observability

---

## 9. Customer Service — Schema Design

### 9.1 Table: customer

```sql
CREATE TABLE customer (
    id UUID PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

---

### 9.2 Constraints

```sql
ALTER TABLE customer
ADD CONSTRAINT uq_customer_email UNIQUE (email);
```

---

### 9.3 Indexes

```sql
CREATE INDEX idx_customer_email ON customer(email);
```

---

### 9.4 Status Values

Allowed values for `status`:

* ACTIVE
* INACTIVE
* BLOCKED

---

### 9.5 Design Decisions

#### UUID as Primary Key

* Suitable for distributed systems
* Avoids ID collision across services

#### Unique Email

* Ensures one account per email
* Used for authentication

#### No Cross-Service Dependencies

* Customer Service owns its data completely

---

### 9.6 Edge Cases

* Duplicate registration attempts
* Email case sensitivity (should be normalized at application level)
* Partial failures during registration

---

### 9.7 Future Enhancements

* Email verification flow
* Password reset tokens
* Soft delete (`is_deleted` flag)
* Audit tracking (`created_by`, `updated_by`)

---

## 10. Database Migration Strategy

We will use **Flyway** for managing database schema changes.

### 10.1 Approach

* Each schema change is versioned using migration scripts
* Migrations are applied automatically on application startup
* Schema evolution is incremental and backward-compatible where possible

---

### 10.2 Naming Convention

Migration files follow the format:

```text
V1__create_customer_table.sql
V2__add_customer_indexes.sql
```

---

### 10.3 Benefits

* Consistent database state across environments
* Eliminates manual schema setup
* Enables safe and traceable schema evolution
* Simplifies onboarding and deployment

---

### 10.4 Service-Level Migration Structure

Each service will maintain its own migration scripts:

```text
customer-service/
  └── src/main/resources/db/migration

product-service/
  └── src/main/resources/db/migration
```

This aligns with the database-per-service pattern and keeps schema ownership isolated.

---
## 11. Order Service — Schema Design

### 11.1 Overview

The Order Service is the core component of the system responsible for managing customer orders.

It maintains:

* Order lifecycle
* Order items
* Total pricing snapshot at time of purchase

---

### 11.2 Table: orders

```sql
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL,
    customer_id UUID NOT NULL,
    order_status VARCHAR(30) NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(10) NOT NULL,
    created_at_utc TIMESTAMP NOT NULL,
    updated_at_utc TIMESTAMP NOT NULL
);
```

---

### 11.3 Constraints

```sql
ALTER TABLE orders
ADD CONSTRAINT uq_order_number UNIQUE (order_number);
```

---

### 11.4 Indexes

```sql
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(order_status);
CREATE INDEX idx_orders_created_at ON orders(created_at_utc);
```

---

### 11.5 Order Status Values

Allowed values for `order_status`:

* CREATED
* PENDING_PAYMENT
* CONFIRMED
* CANCELLED

---

### 11.6 Table: order_items

```sql
CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INT NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    total_price NUMERIC(12, 2) NOT NULL
);
```

---

### 11.7 Constraints

```sql
ALTER TABLE order_items
ADD CONSTRAINT fk_order_items_order
FOREIGN KEY (order_id) REFERENCES orders(id);
```

---

### 11.8 Indexes

```sql
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
```

---

### 11.9 Design Considerations

#### Order Number vs ID

* `id` is internal (UUID)
* `order_number` is external-facing and human-readable

#### Snapshot Pricing

* `unit_price` and `total_price` are stored in order_items
* Ensures historical accuracy even if product price changes later

#### Customer Reference

* `customer_id` is stored without foreign key constraint across services
* Data ownership remains within Customer Service

#### One-to-Many Relationship

* One order can contain multiple order items

---

### 11.10 Edge Cases

* Duplicate order submission (requires idempotency at application level)
* Partial order creation failures
* Price mismatch scenarios
* Large orders with many items

---

### 11.11 Future Enhancements

* Order status history tracking
* Payment reference linking
* Discount and coupon handling
* Tax calculation support

---
## 12. Inventory Service — Schema Design

### 12.1 Overview

The Inventory Service is responsible for managing product stock levels and ensuring consistency during concurrent operations.

It maintains:

* Available stock
* Reserved stock for ongoing orders

---

### 12.2 Table: inventory

```sql
CREATE TABLE inventory (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    available_quantity INT NOT NULL,
    reserved_quantity INT NOT NULL,
    updated_at_utc TIMESTAMP NOT NULL
);
```

---

### 12.3 Constraints

```sql
ALTER TABLE inventory
ADD CONSTRAINT uq_inventory_product UNIQUE (product_id);
```

---

### 12.4 Indexes

```sql
CREATE INDEX idx_inventory_product_id ON inventory(product_id);
```

---

### 12.5 Design Considerations

#### Stock Separation

* `available_quantity` represents sellable stock
* `reserved_quantity` represents stock held for pending orders

#### One Record per Product

* Each product has a single inventory record
* Enforced using unique constraint on `product_id`

#### No Cross-Service Constraints

* `product_id` is stored as reference only
* No foreign key to Product Service

---

### 12.6 Concurrency Considerations

Inventory updates must be handled carefully to avoid race conditions.

Potential approaches (to be implemented later):

* Optimistic locking (version column)
* Pessimistic locking (SELECT FOR UPDATE)
* Atomic update queries

---

### 12.7 Edge Cases

* Concurrent stock updates
* Negative stock due to race conditions
* Reservation not released after failure
* Partial system failures during order processing

---

### 12.8 Future Enhancements

* Add version column for optimistic locking
* Track inventory history
* Support multi-warehouse inventory
* Introduce low-stock alerts

---
## 13. Payment Service — Schema Design

### 13.1 Overview

The Payment Service is responsible for processing and tracking payments for orders.

It maintains:

* Payment attempts
* Payment status
* External payment references

---

### 13.2 Table: payments

```sql
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    payment_reference VARCHAR(100),
    amount NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(10) NOT NULL,
    payment_status VARCHAR(20) NOT NULL,
    payment_method VARCHAR(50),
    created_at_utc TIMESTAMP NOT NULL,
    updated_at_utc TIMESTAMP NOT NULL
);
```

---

### 13.3 Indexes

```sql
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_status ON payments(payment_status);
```

---

### 13.4 Payment Status Values

Allowed values for `payment_status`:

* SUCCESS
* FAILED
* TIMEOUT

---

### 13.5 Design Considerations

#### Order Reference

* `order_id` links payment to order
* No foreign key across services

#### Multiple Payment Attempts

* Multiple rows can exist for same order
* Supports retries and failure scenarios

#### External Reference

* `payment_reference` stores gateway response ID
* Helps in reconciliation and debugging

---

### 13.6 Edge Cases

* Payment timeout but actual success at provider
* Duplicate payment attempts
* Partial failures during processing
* Mismatch between order amount and payment

---

### 13.7 Future Enhancements

* Idempotency keys
* Payment audit logs
* Integration with real payment gateways
* Retry mechanisms

---
