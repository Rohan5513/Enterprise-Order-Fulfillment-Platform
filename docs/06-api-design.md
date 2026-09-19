# 06 — API Design

## 1. General API Principles

### 1.1 REST Conventions

* Use nouns, not verbs
* Use HTTP methods correctly
* Keep endpoints consistent and predictable

---

### 1.2 Standard Response Structure

All APIs will follow a consistent response format:

```json
{
  "data": {},
  "message": "string",
  "timestamp": "ISO-8601"
}
```

---

### 1.3 Error Response Structure

```json
{
  "error": {
    "code": "string",
    "message": "string"
  },
  "timestamp": "ISO-8601"
}
```

---

### 1.4 HTTP Status Codes

* 200 — Success
* 201 — Created
* 400 — Bad Request
* 401 — Unauthorized
* 404 — Not Found
* 500 — Internal Server Error

---

## 2. Customer Service APIs

### 2.1 Register Customer

**Endpoint**

```http
POST /api/v1/customers
```

---

### Request Body

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "password": "StrongPassword123",
  "phone": "9876543210"
}
```

---

### Validation Rules

* Email must be valid format
* Password must meet minimum complexity
* Email must be unique

---

### Response

```json
{
  "data": {
    "customerId": "uuid"
  },
  "message": "Customer registered successfully",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

---

### 2.2 Customer Login

**Endpoint**

```http
POST /api/v1/auth/login
```

---

### Request Body

```json
{
  "email": "john.doe@example.com",
  "password": "StrongPassword123"
}
```

---

### Response

```json
{
  "data": {
    "accessToken": "jwt-token"
  },
  "message": "Login successful",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

---

### 2.3 Get Customer Profile

**Endpoint**

```http
GET /api/v1/customers/{customerId}
```

---

### Response

```json
{
  "data": {
    "id": "uuid",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phone": "9876543210",
    "status": "ACTIVE"
  },
  "message": "Customer retrieved successfully",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

---

### 2.4 Design Considerations

#### Email Uniqueness

* Enforced at DB level
* Validated at API level

#### Password Handling

* Never returned in response
* Stored as hash

#### Stateless Authentication

* JWT-based authentication
* No server-side session storage

---

### 2.5 Edge Cases

* Duplicate email registration
* Invalid credentials
* Non-existent customer ID

---
## 3. Product Service APIs

### 3.1 Get Product List

**Endpoint**

```http
GET /api/v1/products
```

---

### Query Parameters

| Parameter | Type   | Description                |
| --------- | ------ | -------------------------- |
| page      | int    | Page number (default: 0)   |
| size      | int    | Page size (default: 10)    |
| category  | string | Filter by product category |
| status    | string | Filter by product status   |
| minPrice  | number | Minimum price              |
| maxPrice  | number | Maximum price              |

---

### Example Request

```http
GET /api/v1/products?page=0&size=10&category=electronics&minPrice=100
```

---

### Response

```json
{
  "data": {
    "content": [
      {
        "id": "uuid",
        "sku": "SKU123",
        "productName": "Mobile Phone",
        "productCategory": "electronics",
        "unitPrice": 150.00,
        "currencyCode": "INR",
        "productStatus": "ACTIVE"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 100
  },
  "message": "Products retrieved successfully",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

---

### 3.2 Get Product Details

**Endpoint**

```http
GET /api/v1/products/{productId}
```

---

### Response

```json
{
  "data": {
    "id": "uuid",
    "sku": "SKU123",
    "productName": "Mobile Phone",
    "productDescription": "Latest smartphone",
    "productCategory": "electronics",
    "unitPrice": 150.00,
    "currencyCode": "INR",
    "productStatus": "ACTIVE"
  },
  "message": "Product retrieved successfully",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

---

### 3.3 Design Considerations

#### Pagination

* Prevents large data loads
* Improves performance
* Standard approach for listing APIs

#### Filtering

* Enables flexible querying
* Reduces need for multiple endpoints

#### No Business Logic Leakage

* Only necessary fields returned
* Internal DB structure is hidden

---

### 3.4 Edge Cases

* Invalid pagination parameters
* No products found
* Product not found by ID

---
## 4. Order Service APIs

### 4.1 Create Order

**Endpoint**

```http
POST /api/v1/orders
```

---

### Request Body

```json
{
  "customerId": "uuid",
  "items": [
    {
      "productId": "uuid",
      "quantity": 2
    },
    {
      "productId": "uuid",
      "quantity": 1
    }
  ],
  "currencyCode": "INR"
}
```

---

### Request Validation

* `customerId` must exist
* At least one item is required
* Each item must have:

  * valid `productId`
  * quantity > 0
* Duplicate product entries should be merged or rejected

---

### Response

```json
{
  "data": {
    "orderId": "uuid",
    "orderNumber": "ORD-20260001",
    "orderStatus": "CREATED"
  },
  "message": "Order created successfully",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

---

### 4.2 Get Order Details

**Endpoint**

```http
GET /api/v1/orders/{orderId}
```

---

### Response

```json
{
  "data": {
    "id": "uuid",
    "orderNumber": "ORD-20260001",
    "customerId": "uuid",
    "orderStatus": "CREATED",
    "totalAmount": 300.00,
    "currencyCode": "INR",
    "items": [
      {
        "productId": "uuid",
        "quantity": 2,
        "unitPrice": 100.00,
        "totalPrice": 200.00
      }
    ]
  },
  "message": "Order retrieved successfully",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

---

### 4.3 Get Customer Orders

**Endpoint**

```http
GET /api/v1/orders?customerId={customerId}
```

---

### Query Parameters

| Parameter  | Type | Description               |
| ---------- | ---- | ------------------------- |
| customerId | UUID | Filter orders by customer |
| page       | int  | Page number               |
| size       | int  | Page size                 |

---

### Response

```json
{
  "data": {
    "content": [
      {
        "orderId": "uuid",
        "orderNumber": "ORD-20260001",
        "orderStatus": "CONFIRMED",
        "totalAmount": 300.00,
        "currencyCode": "INR"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 25
  },
  "message": "Orders retrieved successfully",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

---

### 4.4 Design Considerations

#### Order Creation Flow (High-Level)

1. Validate request
2. Fetch product details (price, availability)
3. Calculate total amount
4. Create order and order items
5. Set status = CREATED

(Note: Inventory reservation and payment will be handled asynchronously later)

---

#### Pricing Integrity

* Prices are fetched at order time
* Stored in order_items as snapshot
* Ensures historical accuracy

---

#### Stateless Design

* Order API does not maintain session
* Each request is self-contained

---

### 4.5 Edge Cases

* Duplicate order submissions
* Invalid product IDs
* Price changes between request and processing
* Large orders with many items

---
