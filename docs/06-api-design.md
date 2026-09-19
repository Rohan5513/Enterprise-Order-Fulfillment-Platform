# 06 — API Design

## 1. General Principles

* REST: nouns for resources, HTTP methods used correctly
* Base path: `/api/v1`. Additive changes (new optional fields) are allowed within a version; breaking changes require `/api/v2`
* JSON only. Field names in camelCase. Timestamps are ISO-8601 UTC. Money is a decimal number in the major unit (for example `150.00`); the server uses `BigDecimal`
* Each service publishes an OpenAPI spec (springdoc), aggregated at the gateway
* Every request may carry `X-Correlation-Id`; the server generates one if absent and returns it in the response and in error bodies as `traceId`
* Pagination: `page` (default 0), `size` (default 10, max 100), optional `sort`

### 1.1 Success response

```json
{
  "data": {},
  "message": "string",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

### 1.2 Error response

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed",
    "details": [
      { "field": "email", "issue": "must be a valid email address" }
    ]
  },
  "timestamp": "2026-01-01T10:00:00Z",
  "path": "/api/v1/customers",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

`details` is present only for validation errors. Stack traces and internal messages are never returned.

### 1.3 HTTP status codes

| Code | Use                                                              |
| ---- | ---------------------------------------------------------------- |
| 200  | Success                                                          |
| 201  | Resource created (with `Location` header)                        |
| 400  | Malformed or invalid request                                     |
| 401  | Missing, invalid, or expired token                               |
| 403  | Authenticated but not allowed (role)                             |
| 404  | Not found, or not visible to this caller                         |
| 409  | Conflict with current state (duplicate email, cannot cancel)     |
| 422  | Well-formed but semantically rejected (idempotency key reuse)    |
| 429  | Rate limit exceeded                                              |
| 500  | Unexpected server error                                          |
| 503  | A dependency is unavailable                                      |

### 1.4 Error codes

| Code                       | HTTP | When                                                     |
| -------------------------- | ---- | -------------------------------------------------------- |
| VALIDATION_FAILED          | 400  | Field validation failed                                  |
| IDEMPOTENCY_KEY_REQUIRED   | 400  | `Idempotency-Key` header missing                         |
| UNAUTHORIZED               | 401  | No or invalid token; also wrong login credentials        |
| FORBIDDEN                  | 403  | Role not allowed                                         |
| NOT_FOUND                  | 404  | Resource missing or owned by someone else                |
| EMAIL_ALREADY_REGISTERED   | 409  | Duplicate registration                                   |
| ORDER_NOT_CANCELLABLE      | 409  | Order is `CONFIRMED` or already `CANCELLED` by system    |
| INSUFFICIENT_STOCK         | 409  | Admin adjustment would make stock negative               |
| IDEMPOTENCY_KEY_REUSED     | 422  | Same key, different request body                         |
| PRODUCT_NOT_AVAILABLE      | 422  | Product does not exist or is `INACTIVE`                  |
| MIXED_CURRENCY             | 422  | Items in the order have different currencies             |
| RATE_LIMITED               | 429  | Too many requests                                        |
| DEPENDENCY_UNAVAILABLE     | 503  | Product Service unreachable or timed out                 |
| INTERNAL_ERROR             | 500  | Unexpected error                                         |

---

## 2. Authentication and Authorization

* Login returns a signed **JWT access token** (short-lived, 15 minutes). Claims: `sub` = customer ID, `role`, `exp`
* The **API Gateway** validates the token and routes requests. **Each service also validates it**; services never trust "it came from inside the network"
* Identity always comes from the token, never from the request body or query string
* Ownership rule: a `CUSTOMER` can only see and change their own resources. Someone else's resource returns `404`, which does not reveal whether it exists. `ADMIN` can access any

| Endpoint                                        | Access             |
| ----------------------------------------------- | ------------------ |
| `POST /customers`, `POST /auth/login`           | Public             |
| `GET /products`, `GET /products/{id}`           | Public             |
| `GET /customers/me`                             | CUSTOMER, ADMIN    |
| `GET /customers/{customerId}`                   | ADMIN              |
| `POST /products`, `PATCH /products/{id}`        | ADMIN              |
| `GET /inventory/{productId}`, `POST .../adjustments` | ADMIN         |
| `POST /orders`                                  | CUSTOMER           |
| `GET /orders`, `GET /orders/{id}`               | Owner or ADMIN     |
| `POST /orders/{id}/cancel`                      | Owner or ADMIN     |

---

## 3. Customer Service APIs

### 3.1 Register customer: `POST /api/v1/customers`

Request:

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "password": "StrongPassword123",
  "phone": "9876543210"
}
```

Validation: valid email (stored lowercased), password at least 8 characters with upper case, lower case, and a digit, names and phone within column limits.

Response `201`:

```json
{
  "data": { "customerId": "uuid" },
  "message": "Customer registered successfully",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

Errors: `400 VALIDATION_FAILED`, `409 EMAIL_ALREADY_REGISTERED`.

### 3.2 Login: `POST /api/v1/auth/login`

Request: `email`, `password`.

Response `200`:

```json
{
  "data": { "accessToken": "jwt-token", "tokenType": "Bearer", "expiresIn": 900 },
  "message": "Login successful",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

Errors: `401 UNAUTHORIZED` with the **same generic message** ("Invalid email or password") for unknown email, wrong password, and blocked account. Different messages would let an attacker discover which emails are registered.

### 3.3 My profile: `GET /api/v1/customers/me`

Returns `id`, `firstName`, `lastName`, `email`, `phone`, `status`, `role`. Never returns the password hash.

`GET /api/v1/customers/{customerId}` is the ADMIN-only variant.

### 3.4 Design notes

* Email uniqueness is enforced by the database and reported as `409`. This reveals that an email exists; that is an accepted trade-off for a usable sign-up experience
* Rate limiting on login and registration is added at the gateway (`429`)

---

## 4. Product and Inventory APIs

### 4.1 List products: `GET /api/v1/products`

| Parameter | Type   | Description                                              |
| --------- | ------ | -------------------------------------------------------- |
| page      | int    | Page number (default 0)                                  |
| size      | int    | Page size (default 10, max 100)                          |
| category  | string | Filter by category                                       |
| status    | string | `ACTIVE` (default for non-admins) or `INACTIVE` (ADMIN)  |
| minPrice  | number | Minimum price                                            |
| maxPrice  | number | Maximum price                                            |

Response `200`:

```json
{
  "data": {
    "content": [
      {
        "id": "uuid",
        "sku": "SKU123",
        "name": "Mobile Phone",
        "category": "electronics",
        "unitPrice": 150.00,
        "currencyCode": "INR",
        "status": "ACTIVE"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 100,
    "totalPages": 10
  },
  "message": "Products retrieved successfully",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

### 4.2 Product details: `GET /api/v1/products/{productId}`

Same fields as above plus `description`. `404` if not found.

### 4.3 Admin: `POST /api/v1/products`, `PATCH /api/v1/products/{productId}`

* Create: `sku`, `name`, `description`, `category`, `unitPrice`, `currencyCode`. `409` on duplicate SKU
* Patch: any of `name`, `description`, `category`, `unitPrice`, `status`. Price changes never affect existing orders (prices are snapshotted)

### 4.4 Internal product lookup (not exposed through the gateway)

`GET /internal/v1/products?ids=uuid1,uuid2` returns the price, currency, and status of many products in one call. Used by the Order Service so a 10-item order needs one request, not ten.

### 4.5 Admin inventory

* `GET /api/v1/inventory/{productId}` returns `availableQuantity` and `reservedQuantity`
* `POST /api/v1/inventory/{productId}/adjustments` with `{ "delta": 50, "reason": "RESTOCK" }`

Adjustments are **deltas, not absolute values**: reservations change stock concurrently, and an absolute overwrite would erase them. A negative delta that would make `availableQuantity` negative returns `409 INSUFFICIENT_STOCK`.

---

## 5. Order Service APIs

### 5.1 Create order: `POST /api/v1/orders`

Headers: `Authorization: Bearer <token>`, `Idempotency-Key: <uuid>` (required, see 10).

Request:

```json
{
  "items": [
    { "productId": "uuid", "quantity": 2 },
    { "productId": "uuid", "quantity": 1 }
  ]
}
```

There is **no** `customerId` (taken from the token) and **no** `currencyCode` or prices (taken from the products).

Validation:

* 1 to 50 items; each `quantity` between 1 and 100
* Duplicate `productId` values are rejected (`400`), not merged
* Every product must exist and be `ACTIVE` (`422 PRODUCT_NOT_AVAILABLE`)
* All products must share one currency (`422 MIXED_CURRENCY`)

Response `201` with header `Location: /api/v1/orders/{orderId}`:

```json
{
  "data": {
    "orderId": "uuid",
    "orderNumber": "ORD-2026-00000001",
    "orderStatus": "CREATED",
    "totalAmount": 300.00,
    "currencyCode": "INR"
  },
  "message": "Order created successfully",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

Why `201` and not `202`: the order resource exists when the call returns. Only the downstream processing is asynchronous.

Processing steps:

1. Validate input; read the customer from the token; compute the request hash
2. Call the Product Service (internal lookup) **before** any DB transaction: 2 second timeout, one retry, circuit breaker; on failure return `503 DEPENDENCY_UNAVAILABLE`. There is no fallback for prices
3. One short local transaction: insert idempotency key, order, items, status history, and the `OrderCreated` outbox event (see 10 §2.3)
4. The saga continues asynchronously (see 09)

The Order Service does **not** call the Customer Service: a valid token proves the customer existed at login. Blocking a customer takes effect when the token expires.

Retry with the same `Idempotency-Key` returns the original response with `Idempotent-Replayed: true`.

### 5.2 Order details: `GET /api/v1/orders/{orderId}`

```json
{
  "data": {
    "id": "uuid",
    "orderNumber": "ORD-2026-00000001",
    "customerId": "uuid",
    "orderStatus": "PAYMENT_PENDING",
    "cancellationReason": null,
    "totalAmount": 300.00,
    "currencyCode": "INR",
    "items": [
      { "productId": "uuid", "quantity": 2, "unitPrice": 100.00, "totalPrice": 200.00 },
      { "productId": "uuid", "quantity": 1, "unitPrice": 100.00, "totalPrice": 100.00 }
    ],
    "history": [
      { "from": null,      "to": "CREATED",         "at": "2026-01-01T10:00:00Z" },
      { "from": "CREATED", "to": "PAYMENT_PENDING", "at": "2026-01-01T10:00:01Z" }
    ],
    "createdAt": "2026-01-01T10:00:00Z",
    "updatedAt": "2026-01-01T10:00:01Z"
  },
  "message": "Order retrieved successfully",
  "timestamp": "2026-01-01T10:00:02Z"
}
```

`404` if the order does not exist or belongs to another customer (unless ADMIN).

Because processing is asynchronous, clients **poll** this endpoint (every 2 seconds) until the status is `CONFIRMED` or `CANCELLED`. Server-sent events are a future improvement.

### 5.3 List orders: `GET /api/v1/orders`

| Parameter  | Type   | Description                                             |
| ---------- | ------ | ------------------------------------------------------- |
| page, size | int    | Pagination                                              |
| status     | string | Optional filter                                         |
| customerId | UUID   | **ADMIN only.** Customers always see only their own     |

Response items: `orderId`, `orderNumber`, `orderStatus`, `totalAmount`, `currencyCode`, `createdAt`. Newest first.

### 5.4 Cancel order: `POST /api/v1/orders/{orderId}/cancel`

* Allowed while status is `CREATED` or `PAYMENT_PENDING`
* Cancelling an order the customer already cancelled returns `200` with the current state (safe to repeat)
* If the order is `CONFIRMED`, or was cancelled by the system, returns `409 ORDER_NOT_CANCELLABLE`
* Uses the same transition rules and optimistic locking as the saga, so a cancel racing a payment result ends in exactly one consistent outcome (see 09)

### 5.5 Order processing

The asynchronous workflow (inventory reservation, payment, compensation, timeouts) is defined in **09-order-saga.md**. It is deliberately not repeated here, so there is one source of truth.

---

## 6. Design Considerations

* **Stateless:** no server-side sessions; identity comes from the JWT
* **Least data:** responses return only what the client needs; internal table structure is not exposed
* **Consistent contract:** same envelope, same error codes, same pagination everywhere
* **No trust in clients:** identity, prices, and currency are all server-side

---

## 7. Edge Cases

* Duplicate registration attempts
* Expired or malformed tokens
* Retried order creation (idempotency)
* Product deactivated or repriced between browsing and ordering
* Product Service down during order creation
* Cancel racing payment success
* Client polling a long-stuck order (sweeper cancels it)
