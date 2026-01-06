CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    entry_type VARCHAR(50) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(30) NOT NULL,
    reference_type VARCHAR(100) NOT NULL,
    reference_id VARCHAR(100),
    idempotency_key VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ledger_idempotencykey UNIQUE (idempotency_key)
);

CREATE INDEX idx_ledger_entries_account_id
    ON ledger_entries(account_id);

CREATE INDEX idx_ledger_entries_account_created_at
    ON ledger_entries(account_id, created_at DESC);
