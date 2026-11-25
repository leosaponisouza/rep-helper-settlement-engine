# SPEC-001: Monthly Debt Simplification, Idempotent Pix Webhooks & Transactional Outbox

- **Spec ID:** `SPEC-001`
- **Status:** Approved
- **Service:** `rep-helper-settlement-engine`

## 1. Domain Invariants

1. **Zero-Sum Conservation (`SPEC-001`):** Given a set of shared household expenses where each entry records `payerId`, `amount`, and `participantIds`, the sum of all net resident positions equals `0.00` (any cent-splitting remainder is deterministically assigned so total credits equal total debits).
2. **Minimum Transfer Upper Bound (`SPEC-001`):** For `N` residents with non-zero balances, the greedy Min-Cash-Flow algorithm produces at most `N - 1` Pix transfers, eliminating circular and transitive debts.
3. **Exact-Once Pix Webhook Idempotency (`SPEC-001`):** Concurrent or retried deliveries of `POST /api/v1/webhooks/pix` sharing an `Idempotency-Key` (or targeting an already `PAID` `pixTxId`) must settle the `SettlementTransfer` **at most once**, returning `200 OK` with `duplicate = true` on replays.
4. **Atomic Transactional Outbox (`SPEC-001`):** Every state transition (`SETTLEMENT_CYCLE_OPENED`, `SETTLEMENT_TRANSFER_PAID`, `SETTLEMENT_CYCLE_SETTLED`) writes a `PENDING` row into `outbox_events` inside the **same database transaction** as the domain mutation, guaranteeing at-least-once downstream publication without dual-write race conditions.
5. **Explicit Timezone API Contracts:** All HTTP payloads expose `OffsetDateTime` (`ISO-8601` with offset) and enforce Jakarta Bean Validation (`@Valid`).
