CREATE TABLE settlement_cycles (
    id UUID PRIMARY KEY,
    republic_id UUID NOT NULL,
    reference_month VARCHAR(7) NOT NULL,
    total_expenses NUMERIC(14, 2) NOT NULL,
    raw_transfer_count INT NOT NULL,
    optimized_transfer_count INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_cycle_republic_month UNIQUE (republic_id, reference_month)
);

CREATE TABLE settlement_transfers (
    id UUID PRIMARY KEY,
    cycle_id UUID NOT NULL REFERENCES settlement_cycles (id) ON DELETE CASCADE,
    debtor_id UUID NOT NULL,
    creditor_id UUID NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    pix_tx_id VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    settled_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_transfers_cycle_status ON settlement_transfers (cycle_id, status);

CREATE TABLE webhook_receipts (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    pix_tx_id VARCHAR(64) NOT NULL,
    end_to_end_id VARCHAR(64) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_status_created ON outbox_events (status, created_at);
