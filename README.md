# Payment Processing Service

A small RESTful payment-processing microservice built with Grails and GORM. Merchants can register, authenticate with an API key, create payments, capture pending payments, refund successful payments, retrieve payment details, and list their payments with filters and pagination.

This project implements the Block Builders backend developer technical task.

## Technology stack

- Grails 5.3.6
- Groovy 3.0.11
- GORM / Hibernate 5
- H2 database
- Spock integration tests
- Gradle Wrapper 7.2

## Features

- Merchant registration with a generated API key
- Unique merchant email addresses
- API-key authentication for payment endpoints
- Merchant-scoped payment access
- Globally unique external payment references
- Payment lifecycle: `PENDING` → `SUCCESS` → `REFUNDED`
- Transactional capture and refund operations
- Validation and consistent JSON errors
- Filtering by status and creation date
- Pagination using `max` and `offset`
- End-to-end integration tests against the embedded Grails server and H2

## Prerequisites

- JDK 11
- An internet connection on the first build so Gradle can download dependencies

Grails and Gradle do not need to be installed globally because their wrappers are included in the repository.

Verify Java before running the application:

```bash
java -version
```

The project was developed and tested with Java 11. Newer Java releases may not be compatible with Grails 5.3.6 and Gradle 7.2.

## Running the application

### Windows

```powershell
.\gradlew.bat bootRun
```

### Linux or macOS

```bash
./gradlew bootRun
```

The service starts at:

```text
http://localhost:8080
```

Stop it with `Ctrl+C`.

## Running with Docker

Build the multi-stage Java 11 image:

```bash
docker build -t payment-processing-service .
```

Run the service:

```bash
docker run --rm -p 8080:8080 payment-processing-service
```

The container runs as a non-root user and exposes the API at `http://localhost:8080`. It deliberately uses the Grails development environment so the technical-task image is self-contained with an automatically created, in-memory H2 schema. Container data is ephemeral and is discarded when the container stops.

### Development database

Development uses an in-memory H2 database configured with `create-drop`. Data is created when the application starts and discarded when it stops.

The development H2 console is available at:

```text
http://localhost:8080/h2-console
```

Development connection settings:

```text
JDBC URL: jdbc:h2:mem:devDb
User:     yoski
Password: (empty)
```

## Running the tests

Run the integration suite:

### Windows

```powershell
.\gradlew.bat integrationTest
```

### Linux or macOS

```bash
./gradlew integrationTest
```

Run all verification tasks:

```bash
./gradlew check
```

On Windows, use `.\gradlew.bat check`.

The integration tests start the real Grails application on a random port and exercise URL mappings, controllers, services, validation, transactions, JSON rendering, GORM, Hibernate, and H2 together.

## API overview

All API responses use `application/json`.

The complete OpenAPI 3.0.3 specification is available in [`openapi.yaml`](openapi.yaml). It can be imported into [Swagger Editor](https://editor.swagger.io/), Postman, or another OpenAPI-compatible tool to inspect the API and generate a client collection. Swagger UI is not embedded in the service.

| Method | Endpoint | Authentication | Description |
| --- | --- | --- | --- |
| `POST` | `/api/merchants` | None | Create a merchant |
| `POST` | `/api/payments` | `X-API-KEY` | Create a payment |
| `GET` | `/api/payments/{reference}` | `X-API-KEY` | Get one payment |
| `POST` | `/api/payments/{reference}/capture` | `X-API-KEY` | Capture a pending payment |
| `POST` | `/api/payments/{reference}/refund` | `X-API-KEY` | Refund a successful payment |
| `GET` | `/api/payments` | `X-API-KEY` | List the merchant's payments |

## Authentication

Create a merchant to receive an API key. Send that key with every payment request:

```http
X-API-KEY: merchant-api-key
```

Only active merchants are accepted. A merchant can retrieve or modify only its own payments.

## API examples

The examples use `curl` and assume the application is running at `http://localhost:8080`.

### 1. Create a merchant

```bash
curl -i -X POST "http://localhost:8080/api/merchants" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "name": "Test Store",
    "email": "store@test.com"
  }'
```

Successful response: `201 Created`

```json
{
  "id": 1,
  "name": "Test Store",
  "email": "store@test.com",
  "apiKey": "generated-api-key"
}
```

Save the returned `apiKey`; it is required by every payment endpoint. In the following examples, replace `merchant-api-key` with that value.

Merchant behavior:

- `name` is required and surrounding whitespace is removed.
- `email` is required, must be valid, and is globally unique.
- Email addresses are trimmed and stored in lowercase.
- A cryptographically random API key is generated automatically.
- New merchants are active by default.

### 2. Create a payment

```bash
curl -i -X POST "http://localhost:8080/api/payments" \
  -H "X-API-KEY: merchant-api-key" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "reference": "INV-10001",
    "amount": 120.50,
    "currency": "USD",
    "description": "Order payment"
  }'
```

Successful response: `201 Created`

```json
{
  "id": 1,
  "reference": "INV-10001",
  "amount": 120.5000,
  "currency": "USD",
  "description": "Order payment",
  "status": "PENDING",
  "merchantId": 1,
  "dateCreated": "<timestamp>",
  "lastUpdated": "<timestamp>"
}
```

Payment behavior:

- `reference` is required, trimmed, and globally unique.
- `amount` is required and must be greater than zero.
- Amounts are stored with precision 19 and scale 4.
- `currency` is required, trimmed, and converted to uppercase.
- `description` is optional and trimmed when supplied.
- Every new payment starts with the `PENDING` status.

### 3. Get payment details

```bash
curl -i "http://localhost:8080/api/payments/INV-10001" \
  -H "X-API-KEY: merchant-api-key" \
  -H "Accept: application/json"
```

Successful response: `200 OK`

```json
{
  "id": 1,
  "reference": "INV-10001",
  "amount": 120.5000,
  "currency": "USD",
  "description": "Order payment",
  "status": "PENDING",
  "merchantId": 1,
  "dateCreated": "<timestamp>",
  "lastUpdated": "<timestamp>"
}
```

If the reference does not exist or belongs to another merchant, the API returns `404 Not Found`.

### 4. Capture a payment

```bash
curl -i -X POST "http://localhost:8080/api/payments/INV-10001/capture" \
  -H "X-API-KEY: merchant-api-key" \
  -H "Accept: application/json"
```

Successful response: `200 OK`. The returned payment has:

```json
{
  "reference": "INV-10001",
  "status": "SUCCESS"
}
```

The complete payment representation is returned; the shortened response above highlights the changed fields.

Capture rules:

- Only a `PENDING` payment can be captured.
- A successful capture changes the status to `SUCCESS`.
- Capturing an already captured payment returns `409 Conflict` with error code `10`.

### 5. Refund a payment

```bash
curl -i -X POST "http://localhost:8080/api/payments/INV-10001/refund" \
  -H "X-API-KEY: merchant-api-key" \
  -H "Accept: application/json"
```

Successful response: `200 OK`. The returned payment has:

```json
{
  "reference": "INV-10001",
  "status": "REFUNDED"
}
```

The complete payment representation is returned; the shortened response above highlights the changed fields.

Refund rules:

- Only a `SUCCESS` payment can be refunded.
- A successful refund changes the status to `REFUNDED`.
- Refunding an already refunded payment returns `409 Conflict` with error code `10`.

### 6. List merchant payments

```bash
curl -i "http://localhost:8080/api/payments" \
  -H "X-API-KEY: merchant-api-key" \
  -H "Accept: application/json"
```

Successful response: `200 OK`

```json
{
  "payments": [
    {
      "id": 1,
      "reference": "INV-10001",
      "amount": 120.5000,
      "currency": "USD",
      "description": "Order payment",
      "status": "REFUNDED",
      "merchantId": 1,
      "dateCreated": "<timestamp>",
      "lastUpdated": "<timestamp>"
    }
  ],
  "max": 10,
  "offset": 0,
  "total": 1
}
```

Only payments owned by the authenticated merchant are returned. Results are ordered by `dateCreated` descending.

#### Status filter

Supported values are `PENDING`, `SUCCESS`, `FAILED`, and `REFUNDED`.

```bash
curl -i "http://localhost:8080/api/payments?status=SUCCESS" \
  -H "X-API-KEY: merchant-api-key" \
  -H "Accept: application/json"
```

#### Date filters

`fromDate` and `toDate` filter on `dateCreated` and are inclusive. ISO-8601 UTC values can be supplied as URL-encoded query parameters:

```bash
curl -i --get "http://localhost:8080/api/payments" \
  -H "X-API-KEY: merchant-api-key" \
  -H "Accept: application/json" \
  --data-urlencode "fromDate=2026-08-01T00:00:00Z" \
  --data-urlencode "toDate=2026-08-31T23:59:59Z"
```

`fromDate` cannot be later than `toDate`.

#### Pagination

```bash
curl -i "http://localhost:8080/api/payments?max=10&offset=0" \
  -H "X-API-KEY: merchant-api-key" \
  -H "Accept: application/json"
```

| Parameter | Default | Validation |
| --- | ---: | --- |
| `max` | `10` | Between `1` and `100` |
| `offset` | `0` | Zero or greater |

Filters can be combined:

```bash
curl -i "http://localhost:8080/api/payments?status=SUCCESS&max=20&offset=0" \
  -H "X-API-KEY: merchant-api-key" \
  -H "Accept: application/json"
```

## Payment lifecycle

```text
Create             Capture             Refund
  │                   │                   │
  ▼                   ▼                   ▼
PENDING ──────────► SUCCESS ──────────► REFUNDED
```

`FAILED` is defined as a supported status for domain completeness and filtering, but this service does not currently communicate with an external payment processor and therefore does not automatically transition a payment to `FAILED`.

## Error responses

All handled API errors use this JSON shape:

```json
{
  "errorCode": "10",
  "error": "Payment already captured"
}
```

| HTTP status | Error code | Meaning | Example |
| ---: | --- | --- | --- |
| `400 Bad Request` | `02` | Request or filter validation failed | Amount must be greater than zero |
| `401 Unauthorized` | `01` | API key is missing, invalid, or belongs to an inactive merchant | `API key is required` |
| `404 Not Found` | `03` | Payment was not found for the authenticated merchant | `Payment not found` |
| `409 Conflict` | `04` | A unique merchant email or payment reference already exists | `A payment with this reference already exists` |
| `409 Conflict` | `10` | The requested payment status transition is invalid | `Payment already captured` |

Example missing API key response:

```json
{
  "errorCode": "01",
  "error": "API key is required"
}
```

Example duplicate reference response:

```json
{
  "errorCode": "04",
  "error": "A payment with this reference already exists"
}
```

Example invalid transition response:

```json
{
  "errorCode": "10",
  "error": "Only successful payments can be refunded"
}
```

## Architecture

The application follows a thin-controller/service-layer design:

```text
HTTP / JSON
    │
    ▼
URL mappings
    │
    ▼
Controllers ──► command objects / validation
    │
    ▼
Services ──► business rules and transactions
    │
    ▼
GORM domain models
    │
    ▼
H2 database
```

Important locations:

```text
grails-app/controllers/       REST controllers and URL mappings
grails-app/services/          Merchant and payment business logic
grails-app/domain/            GORM domain models
src/main/groovy/.../dto/      Command and response DTOs
src/integration-test/         End-to-end API integration tests
grails-app/conf/              Application and data-source configuration
```

Controllers delegate business logic to `MerchantService` and `PaymentService`. Capture and refund run transactionally and retrieve the payment with a database lock to protect status transitions from concurrent updates. Read operations are marked read-only.

## Integration-test coverage

The integration suite covers:

- Merchant creation, normalization, API-key generation, and persistence
- Invalid merchant data and duplicate email addresses
- Missing, unknown, and inactive merchant API keys
- Payment creation, retrieval, capture, and refund
- Initial and persisted payment statuses
- Invalid amounts and duplicate payment references
- Invalid capture and refund transitions
- Cross-merchant payment isolation
- Status filtering and pagination
- Consistent JSON status and error responses

## Assumptions and implementation notes

- Payment references are globally unique across all merchants, matching the domain requirement that `reference` is unique.
- Merchant emails are globally unique after trimming and lowercase normalization.
- Every payment endpoint requires an active merchant API key, including get, list, capture, and refund.
- Payment lookup is scoped to the authenticated merchant. A reference owned by another merchant is treated as not found and returns `404`.
- Capture is synchronous and succeeds immediately; no external payment gateway is called.
- Refunds are full-payment status transitions. Partial refunds and refund amounts are outside the current scope.
- The `FAILED` status is reserved for future processor integration and is not produced by the current API.
- Currency values are normalized to uppercase but are not validated against an ISO-4217 currency list.
- API keys are generated from 32 cryptographically secure random bytes and encoded using URL-safe Base64 without padding.
- API keys are returned only in the merchant-creation response; there is no key rotation or recovery endpoint.
- The development and test environments use in-memory H2 databases. The production profile is only a baseline H2 file configuration and should be replaced or hardened for a real deployment.
- No merchant-management endpoint is included because the task requires only merchant creation.
- No delete-payment operation is included; transaction history is retained.
- Timestamps are generated automatically by GORM.

## Production considerations

For a production deployment, consider adding:

- A production-grade database such as PostgreSQL
- Database migrations with Liquibase or Flyway
- Hashed API-key storage, rotation, revocation, and audit logging
- TLS termination and rate limiting
- Idempotency handling for retries
- An external payment-provider integration
- Structured logging, metrics, tracing, and alerting
- Production deployment configuration, persistent storage, and health probes
- Additional concurrency, security, and failure-recovery tests
