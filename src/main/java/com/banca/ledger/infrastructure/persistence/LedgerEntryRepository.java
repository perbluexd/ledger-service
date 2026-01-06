package com.banca.ledger.infrastructure.persistence;

import com.banca.ledger.domain.model.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;

import java.time.Instant;

import java.util.Optional;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry,Long> {
    Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey);

    Page<LedgerEntry> findByAccountId(Long accountId, Pageable pageable);

    Page<LedgerEntry> findByAccountIdAndCreatedAtBetween(Long accountId, Instant from, Instant to, Pageable pageable);
}
