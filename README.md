# Enterprise Order & Fulfillment Platform

Event-driven order platform built with Spring Boot, PostgreSQL and Kafka. Design docs live in [`docs/`](docs/).

## Prerequisites

* Java 21+ (match `java.version` in the root `pom.xml`)
* Docker with Docker Compose
* Maven (only once, to generate the wrapper; after that use `./mvnw`)

## Run locally

```bash
# 1. Start PostgreSQL and Kafka
docker compose up -d

# 2. Build and test everything
./mvnw verify          # Windows: mvnw.cmd verify

# 3. Run a service (example: order-service on port 8083)
./mvnw -pl services/order-service spring-boot:run

# 4. Check it
curl http://localhost:8083/actuator/health
curl http://localhost:8083/actuator/prometheus
```

## Services and ports

| Service              | Port | Database        |
| -------------------- | ---- | --------------- |
| customer-service     | 8081 | customer_db     |
| product-service      | 8082 | product_db      |
| order-service        | 8083 | order_db        |
| inventory-service    | 8084 | inventory_db    |
| payment-service      | 8085 | payment_db      |
| notification-service | 8086 | notification_db |

## Status

Design complete; walking skeleton in place. Business logic is added milestone by milestone (see docs).
