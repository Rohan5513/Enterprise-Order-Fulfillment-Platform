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
