-- V1__init_ledger_operations_and_entries.sql
-- Esquema final: ledger_operations (idempotencia + referencia) y ledger_entries con FK a operation_id

-- =========================
-- 1) Tabla: ledger_operations
-- =========================
CREATE TABLE ledger_operations (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(512) NOT NULL,
    reference_type VARCHAR(100) NOT NULL,
    reference_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ledger_operations_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_ledger_operations_reference
    ON ledger_operations(reference_type, reference_id);

-- =========================
-- 2) Tabla: ledger_entries
-- =========================
CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    entry_type VARCHAR(50) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    operation_id UUID NOT NULL,
    CONSTRAINT fk_ledger_entries_operation
        FOREIGN KEY (operation_id) REFERENCES ledger_operations(id)
);

CREATE INDEX idx_ledger_entries_account_id
    ON ledger_entries(account_id);

CREATE INDEX idx_ledger_entries_account_created_at
    ON ledger_entries(account_id, created_at DESC);

CREATE INDEX idx_ledger_entries_operation_id
    ON ledger_entries(operation_id);
