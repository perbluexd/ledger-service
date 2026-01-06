package com.banca.ledger.application.service;

import com.banca.ledger.api.dto.CreateLedgerEntryRequest;
import com.banca.ledger.domain.model.LedgerEntry;
import com.banca.ledger.infrastructure.persistence.LedgerEntryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class LedgerEntryCommandService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerEntryCommandService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public LedgerEntry createEntry(CreateLedgerEntryRequest request) {
        // Defensive checks (DTO already has @Valid, but we keep core guards here too)
        if (request == null) {
            throw new IllegalArgumentException("El request no puede ser null");
        }
        if (request.getAccountId() == null) {
            throw new IllegalArgumentException("accountId es obligatorio");
        }
        if (request.getEntryType() == null) {
            throw new IllegalArgumentException("entryType es obligatorio (DEBIT/CREDIT)");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount debe ser mayor a 0");
        }
        if (request.getCurrency() == null) {
            throw new IllegalArgumentException("currency es obligatorio");
        }
        if (request.getReferenceType() == null) {
            throw new IllegalArgumentException("referenceType es obligatorio");
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey es obligatorio");
        }

        // Idempotency: if already created, return existing entry
        return ledgerEntryRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .orElseGet(() -> createAndPersist(request));
    }

    private LedgerEntry createAndPersist(CreateLedgerEntryRequest request) {
        try {
            LedgerEntry entry = new LedgerEntry(
                    request.getAccountId(),
                    request.getEntryType(),
                    request.getAmount(),
                    request.getCurrency(),
                    request.getReferenceType(),
                    request.getReferenceId(),
                    request.getIdempotencyKey()
            );
            // createdAt is set inside the entity constructor (Instant.now()).
            return ledgerEntryRepository.save(entry);
          // Respecto a la excepción DataIntegrityViolationException lo que hace es indicar que
            // la operación viola una restricción de integridad en la base de datos

        } catch (DataIntegrityViolationException ex) {
            // Concurrency safety: if a duplicate key happened, return the existing entry
            return ledgerEntryRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .orElseThrow(() -> ex);
        }
    }
}
