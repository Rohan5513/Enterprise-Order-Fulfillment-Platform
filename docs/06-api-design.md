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
