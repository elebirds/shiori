# CLAUDE.md — AI Collaboration Rules for Shiori (栞)

This document defines **non-negotiable rules** for any AI assistant working in this repository.

Goal: keep the codebase **consistent, secure, testable, and interview-ready**.

---

## 0) Project Context (Read First)

**Shiori** is a Core/Edge polyglot microservices project:

- **Core (Java / Spring Boot + Spring Cloud)**: gateway, user, product(stock), order(outbox, timeout cancel), admin.
- **Edge (Go / Gin + WebSocket)**: notify service that consumes MQ events and pushes notifications to clients.

**Key engineering principles:**
- Inventory correctness relies on **DB atomic update**, not on distributed locks.
- Cross-service consistency is **event-driven + eventual consistency** via **Transactional Outbox + Relay**.
- Timeout cancellation uses **RabbitMQ TTL + DLX** and **re-checks order status** on consumption.
- Messaging is **at-least-once**, therefore **idempotency** is mandatory.

---

## 1) AI Operating Protocol

### 1.1 Must Do
- **Ask for intent** only when requirements are ambiguous; otherwise proceed with a reasonable default.
- **Keep changes minimal and scoped**. Prefer small diffs over refactors.
- **Explain trade-offs** briefly in PR-style notes (why this approach).
- **Update docs** when behavior changes (README/architecture docs).
- **Add/adjust tests** for any behavioral change in core services.

### 1.2 Must Not Do
- Do not introduce new frameworks/libraries unless explicitly requested.
- Do not change service boundaries (Core vs Edge) without explicit instruction.
- Do not “optimize” by adding heavy distributed transaction frameworks (e.g., Seata) unless requested.
- Do not add Redis distributed locks for inventory correctness.
- Do not remove idempotency checks to “simplify logic”.
- Do not rename public API endpoints or event schemas without migration plan.

---

## 2) Architecture & Boundary Rules

### 2.1 Service Ownership
- `product-service` **owns SKU stock** and is the **single writer** of stock.
- `order-service` **owns orders and order state machine**.
- `notify-service` **owns WebSocket sessions and push delivery**.
- `gateway-service` **owns auth enforcement and routing**.

AI must not implement cross-service DB joins. All cross-service reads/writes must happen via:
- synchronous API calls (Feign) **or**
- asynchronous events (RabbitMQ).

### 2.2 Inventory Correctness (Hard Rule)
Inventory decrement must use a DB-atomic pattern similar to:

```sql
UPDATE sku
SET stock = stock - :qty
WHERE sku_id = :skuId AND stock >= :qty;
````

* Interpret `affected_rows == 1` as success; otherwise out-of-stock.
* Redis may be used for **cache/anti-bot/idempotency/hotspot protection**, not correctness.

### 2.3 Messaging Semantics (Hard Rule)

* MQ delivery is **At-Least-Once**.
* Consumers must be **idempotent**:

  * each event includes `eventId` and `type`
  * consumer uses DB unique key / dedup table / Redis SETNX to prevent duplicate side-effects

### 2.4 Transactional Outbox (Hard Rule)

When core services publish domain events:

* Write business data + outbox record in **one local DB transaction**.
* Use a relay (scheduler/worker) to publish outbox events to RabbitMQ and mark them sent.
* Outbox table must include at least: `id`, `event_id`, `aggregate_id`, `type`, `payload`, `status`, `retry_count`, `created_at`, `sent_at`.

### 2.5 Timeout Cancellation via TTL + DLX

* Do **not** “cancel” already-published delayed messages.
* On consuming `OrderTimeout` from DLQ:

  * **re-check** current order status
  * if `PAID`, ignore (idempotent)
  * if `UNPAID`, cancel + rollback stock + emit `OrderCanceled`

---

## 3) Code Style & Conventions

### 3.1 Java (Core)

* Java version: **21**
* Prefer layered structure:

  * `controller` (HTTP)
  * `service` (business rules)
  * `repository/mapper` (DB)
  * `domain` (entities/value objects)
  * `config` (Spring configs)
* Use **DTOs** for request/response; do not expose entities directly to controllers.
* Use a unified response format `Result<T>` and a consistent error code system.
* Use Flyway migrations; **no auto-DDL**.

**Logging**

* Structured logs preferred; must include `traceId` when available.
* Never log secrets (passwords, tokens).

### 3.2 Go (Edge)

* Go version: **1.26**
* Keep notify service simple:

  * MQ consumer
  * session registry (in-memory)
  * websocket push
* No complex business rules in Go edge service.
* Ensure graceful shutdown: close consumers, close ws conns.

### 3.3 Naming

* Services: `*-service`
* Events: `OrderCreated`, `OrderPaid`, `OrderTimeout`, `OrderCanceled`
* IDs:

  * `orderNo` / `paymentNo` are stable business identifiers
  * `eventId` is a UUID (or equivalent)

---

## 4) API & Event Schema Rules

### 4.1 API Compatibility

* Avoid breaking changes. If changes are necessary:

  * add new fields as optional
  * version endpoints only if required
  * update docs and migration steps

### 4.2 Event Envelope (Recommended)

All events should follow a consistent envelope:

```json
{
  "eventId": "uuid",
  "type": "OrderPaid",
  "aggregateId": "orderNo",
  "createdAt": "ISO-8601",
  "payload": { }
}
```

`payload` is type-specific and must be backward-compatible (additive changes only).

---

## 5) Testing Requirements

### 5.1 Core Services

* Must include unit tests for:

  * order state transitions
  * idempotency logic
  * outbox relay publishing rules
* Integration tests (preferred) for:

  * DB atomic stock decrement
  * MQ publish/consume for at least one event

### 5.2 Edge Service

* At minimum:

  * unit test for routing logic (event -> user sessions)
  * smoke test instructions in docs

---

## 6) Performance & Observability (When adding features)

* Expose Prometheus metrics where applicable:

  * Spring Boot via Actuator
  * notify service: online connections, push success/fail, mq lag
* If adding new endpoints:

  * keep p95 and error rate in mind; avoid N+1 calls
* Load tests live in `/perf` and must be kept up-to-date when APIs change.

---

## 7) Safe Defaults for AI Changes

When unsure:

* Prefer DB-atomic correctness over Redis locks.
* Prefer eventual consistency over distributed transactions.
* Prefer additive, backwards-compatible schema changes.
* Prefer simple, readable code over clever abstractions.

---

## 8) Output Format for AI Suggestions

When proposing a change, AI should provide:

1. **Summary** (what/why)
2. **Files changed** (list)
3. **Diff-level guidance** (key code blocks)
4. **Test plan** (how to verify locally)
5. **Rollback plan** (if applicable)

---

## 9) Non-Goals (Explicitly Out of Scope Unless Requested)

* Real payment gateway integration (only simulated payments by default)
* Full IM system with persistence/ACK/ordering guarantees
* Multi-region active-active setups
* Strong distributed transactions across services (Seata/TCC) by default

---

## 10) Quick Checklist (Before Final Answer)

* [ ] Respects service boundaries (no cross-DB join)
* [ ] Preserves idempotency & at-least-once semantics
* [ ] Inventory correctness uses DB-atomic update
* [ ] Timeout cancellation uses TTL+DLX with status re-check
* [ ] Flyway migrations updated if schema changes
* [ ] Tests updated or added
* [ ] Docs updated if behavior/schema changed

