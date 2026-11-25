# rep-helper-settlement-engine

[![CI](https://github.com/leosaponisouza/rep-helper-settlement-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/leosaponisouza/rep-helper-settlement-engine/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21%20(Virtual%20Threads)-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4.3-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16%20%2B%20Flyway-4169E1?style=flat-square&logo=postgresql&logoColor=white)
[![SDD Guardrails](https://img.shields.io/badge/SDD-sdd--spring--guardrails-5C2D91?style=flat-square)](https://github.com/leosaponisouza/sdd-spring-guardrails)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)

Financial settlement microservice for the [**RepHelper**](https://github.com/leosaponisouza/rep-helper) ecosystem, built on **Java 21 (Virtual Threads)** and **Spring Boot 3.4.3**.

At month-end, shared student households accumulate dozens of cross-paid bills (rent, electricity, groceries, internet). Instead of $O(N^2)$ pairwise reimbursements, this service computes the **mathematically minimal Pix transfer graph (`Min-Cash-Flow`)**, processes **concurrent/retried Pix payment webhooks with exact-once idempotency**, and publishes downstream integration events via a **Transactional Outbox**.

---

## Architecture & Concurrency Model

```mermaid
sequenceDiagram
    participant Client as RepHelper API / Admin
    participant Engine as Settlement Engine (Java 21)
    participant DB as PostgreSQL 16 (ACID)
    participant Pix as Pix Gateway Webhooks
    participant Broker as Outbox Relay -> Downstream

    Client->>Engine: POST /api/v1/settlements/cycles (expenses[])
    Engine->>Engine: Min-Cash-Flow in integer cents (<= N-1 transfers)
    Engine->>DB: TX: insert cycle + transfers + Outbox(CYCLE_OPENED)
    Pix->>Engine: POST /api/v1/webhooks/pix (Idempotency-Key, pixTxId)
    Engine->>DB: TX: UNIQUE(idempotency_key) + @Version optimistic lock + Outbox(TRANSFER_PAID)
    Pix-->>Engine: Concurrent retry / duplicate delivery
    Engine-->>Pix: 200 OK (duplicate=true, zero double-settlement)
    Engine->>Broker: Poll PENDING outbox_events -> mark PUBLISHED + Micrometer metrics
```

### Engineering Highlights

| Capability | Implementation |
|---|---|
| **Min-Cash-Flow Simplification (`DebtSimplificationEngine`)** | Operates strictly in integer cents (`long`) with deterministic remainder allocation so $\sum \text{balances} = 0$ holds without floating-point drift, collapsing transitive/circular debts to at most $N - 1$ Pix transfers in $O(N \log N)$. |
| **Exact-Once Pix Webhook Idempotency (`PixWebhookService`)** | Combines a `UNIQUE(idempotency_key)` receipt ledger (`webhook_receipts`) with JPA `@Version` optimistic locking inside isolated `REQUIRES_NEW` transactions. Verified against **20 concurrent Virtual Threads** hitting the same `pixTxId` simultaneously (`1` settled, `19` deduplicated `200 OK`). |
| **Transactional Outbox (`OutboxRelayService`)** | Eliminates dual-write anomalies between database state (`PAID` / `SETTLED`) and event publication (`SETTLEMENT_CYCLE_OPENED`, `SETTLEMENT_TRANSFER_PAID`, `SETTLEMENT_CYCLE_SETTLED`). |
| **Observability (`Micrometer` + `Prometheus`)** | Exposes domain counters (`settlement.transfers.paid`, `settlement.webhooks.deduplicated`, `settlement.outbox.published`) via `/actuator/prometheus`. |
| **Executable SDD Guardrails** | Enforces [`SPEC-001`](docs/specs/SPEC-001-settlement-and-outbox.md) traceability and boundary rules at test time via [`sdd-spring-guardrails`](https://github.com/leosaponisouza/sdd-spring-guardrails). |

---

## API Overview

| Method & Path | Purpose |
|---|---|
| `POST /api/v1/settlements/cycles` | Computes Min-Cash-Flow plan for `(republicId, referenceMonth)` and opens Pix transfers |
| `GET /api/v1/settlements/cycles/{cycleId}` | Returns cycle status (`OPEN` / `SETTLED`), reduction metrics, and transfer statuses |
| `POST /api/v1/webhooks/pix` | Idempotent Pix settlement callback (`Idempotency-Key` header required) |
| `POST /api/v1/settlements/outbox/relay` | Relays pending transactional outbox events in FIFO order |
| `GET /actuator/prometheus` | Prometheus metrics endpoint |

---

## Verification

```bash
mvn -B test
```

Covers:
1. **`ArchitectureGuardrailsTest`** — verifies `@SpecContract` -> `docs/specs/SPEC-001-settlement-and-outbox.md` traceability, zero `@Entity` leaks, `OffsetDateTime` contracts, and `@Valid` boundaries.
2. **`DebtSimplificationEngineTest`** — verifies $N - 1$ upper bound on circular graphs and exact 1-cent remainder conservation (`R$ 100.00 / 3`).
3. **`SettlementConcurrencyAndOutboxIntegrationTest`** — fires 20 concurrent Java 21 Virtual Threads against `/api/v1/webhooks/pix` to prove exact-once settlement, automatic cycle closure, and atomic Outbox relay.

---

## License

Released under the [MIT License](LICENSE).
