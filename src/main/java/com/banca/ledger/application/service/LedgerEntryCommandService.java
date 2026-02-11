package com.banca.ledger.application.service;

import com.banca.ledger.api.dto.CreateCompositeLedgerMovementRequest;
import com.banca.ledger.api.dto.CreateLedgerEntryRequest;
import com.banca.ledger.api.mapper.CompositeMovementAssembler;
import com.banca.ledger.application.exception.ConflictException;
import com.banca.ledger.application.exception.NotFoundException;
import com.banca.ledger.domain.enums.EntryType;
import com.banca.ledger.domain.enums.ReferenceType;
import com.banca.ledger.domain.model.LedgerEntry;
import com.banca.ledger.domain.model.LedgerOperation;
import com.banca.ledger.infrastructure.persistence.LedgerEntryRepository;
import com.banca.ledger.infrastructure.persistence.LedgerOperationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class LedgerEntryCommandService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerOperationRepository ledgerOperationRepository;
    private final CompositeMovementAssembler compositeMovementAssembler;

    public LedgerEntryCommandService(
            LedgerEntryRepository ledgerEntryRepository,
            LedgerOperationRepository ledgerOperationRepository,
            CompositeMovementAssembler compositeMovementAssembler
    ) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerOperationRepository = ledgerOperationRepository;
        this.compositeMovementAssembler = compositeMovementAssembler;
    }

    // ===============================
    // UC-1: Crear un asiento contable (1 entry)
    // ===============================
    @Transactional
    public LedgerEntry createEntry(CreateLedgerEntryRequest request) {
        validateCreateEntryRequest(request);

        LedgerOperation operation = getOrCreateOperation(
                request.getIdempotencyKey(),
                request.getReferenceType(),
                request.getReferenceId()
        );

        // UC-1: una operación debe tener EXACTAMENTE 1 entry
        List<LedgerEntry> existingEntries = ledgerEntryRepository.findByOperationId(operation.getId());

        if (existingEntries.size() > 1) {
            throw new IllegalStateException(
                    "Inconsistencia: operación " + operation.getId() +
                            " tiene " + existingEntries.size() +
                            " entries, se esperaba exactamente 1"
            );
        }

        if (existingEntries.size() == 1) {
            // Idempotencia: ya existe, devolvemos la misma
            return existingEntries.get(0);
        }

        LedgerEntry newEntry = new LedgerEntry(
                request.getAccountId(),
                request.getEntryType(),
                request.getAmount(),
                request.getCurrency(),
                operation
        );

        return ledgerEntryRepository.save(newEntry);
    }

    // ===============================
    // UC-2: Registrar movimiento compuesto (2 entries: DEBIT + CREDIT)
    // ===============================
    @Transactional
    public OperationEntries recordCompositeMovement(CreateCompositeLedgerMovementRequest request) {
        validateCompositeRequest(request);

        LedgerOperation operation = getOrCreateOperation(
                request.getIdempotencyKey(),
                request.getReferenceType(),
                request.getReferenceId()
        );

        // Idempotencia: si ya existe por key, devolvemos lo existente (esperamos 2 entries)
        List<LedgerEntry> existingEntries = ledgerEntryRepository.findByOperationId(operation.getId());
        if (!existingEntries.isEmpty()) {
            if (existingEntries.size() != 2) {
                throw new IllegalStateException(
                        "Inconsistencia: operación " + operation.getId() + " tiene " +
                                existingEntries.size() + " entradas, se esperaban 2"
                );
            }
            return new OperationEntries(operation, existingEntries);
        }

        // Creamos y guardamos las 2 entries (DEBIT + CREDIT)
        List<LedgerEntry> newEntries = compositeMovementAssembler.toEntries(request, operation);

        if (newEntries == null || newEntries.size() != 2) {
            throw new IllegalStateException("El compositeMovementAssembler debe generar exactamente 2 entradas");
        }

        List<LedgerEntry> saved = ledgerEntryRepository.saveAll(newEntries);
        return new OperationEntries(operation, saved);
    }

    @Transactional
    public OperationEntries reverseOperation(UUID operationId) {
        if (operationId == null) {
            throw new IllegalArgumentException("operationId no puede ser null");
        }

        LedgerOperation originalOp = ledgerOperationRepository.findById(operationId)
                .orElseThrow(() -> new NotFoundException("Operación no encontrada: " + operationId));

        List<LedgerEntry> originalEntries = ledgerEntryRepository.findByOperationId(originalOp.getId());
        if (originalEntries == null || originalEntries.isEmpty()) {
            throw new IllegalStateException("No se encontraron asientos para la operación: " + operationId);
        }

        // 1) Crear una NUEVA operación para la reversión (no reutilizar la original)
        // Aquí puedes definir un referenceType específico tipo REVERSAL si lo agregas a tu enum.
        // Si no lo tienes aún, puedes reutilizar el mismo referenceType y referenceId con un sufijo.
        LedgerOperation reversalOp = ledgerOperationRepository.save(
                new LedgerOperation(
                        "reversal:" + originalOp.getId(),   // idempotencyKey mínimo (mejorable)
                        originalOp.getReferenceType(),
                        originalOp.getReferenceId()
                )
        );

        // 2) Generar entries inversos
        List<LedgerEntry> reversedEntries = new ArrayList<>();

        for (LedgerEntry entry : originalEntries) {
            EntryType reversedType;
            if (entry.getEntryType() == EntryType.CREDIT) {
                reversedType = EntryType.DEBIT;
            } else if (entry.getEntryType() == EntryType.DEBIT) {
                reversedType = EntryType.CREDIT;
            } else {
                throw new IllegalStateException("Tipo de asiento desconocido: " + entry.getEntryType());
            }

            reversedEntries.add(new LedgerEntry(
                    entry.getAccountId(),
                    reversedType,
                    entry.getAmount(),
                    entry.getCurrency(),
                    reversalOp
            ));
        }

        if (reversedEntries.isEmpty()) {
            throw new IllegalStateException("No se pudieron crear asientos inversos para: " + operationId);
        }

        // 3) Guardar en batch
        List<LedgerEntry> saved = ledgerEntryRepository.saveAll(reversedEntries);

        return new OperationEntries(reversalOp, saved);
    }



    /**
     * Idempotencia robusta:
     * - find por idempotencyKey
     * - si no existe, intentamos insert
     * - si hay carrera, DataIntegrityViolationException y volvemos a find
     * - si existe, validamos que referenceType/referenceId coincidan
     */
    private LedgerOperation getOrCreateOperation(String idempotencyKey, ReferenceType referenceType, String referenceId) {
        return ledgerOperationRepository.findByIdempotencyKey(idempotencyKey)
                .map(op -> ensureSameReference(op, referenceType, referenceId))
                .orElseGet(() -> {
                    try {
                        return ledgerOperationRepository.save(
                                new LedgerOperation(idempotencyKey, referenceType, referenceId)
                        );
                    } catch (DataIntegrityViolationException e) {
                        LedgerOperation op = ledgerOperationRepository.findByIdempotencyKey(idempotencyKey)
                                .orElseThrow(() -> new IllegalStateException(
                                        "No se pudo recuperar la operación tras conflicto de idempotencia", e));
                        return ensureSameReference(op, referenceType, referenceId);
                    }
                });
    }

    private LedgerOperation ensureSameReference(LedgerOperation op, ReferenceType referenceType, String referenceId) {
        if (op.getReferenceType() != referenceType || !Objects.equals(op.getReferenceId(), referenceId)) {
            throw new ConflictException(
                    "Conflicto de idempotencia: la idempotencyKey ya existe pero con otra referencia. " +
                            "Existente: (" + op.getReferenceType() + ", " + op.getReferenceId() + ") " +
                            "Nueva: (" + referenceType + ", " + referenceId + ")"
            );
        }
        return op;
    }

    private void validateCreateEntryRequest(CreateLedgerEntryRequest request) {
        if (request == null) throw new IllegalArgumentException("El request no puede ser null");
        if (request.getAccountId() == null) throw new IllegalArgumentException("accountId es obligatorio");
        if (request.getEntryType() == null) throw new IllegalArgumentException("entryType es obligatorio");
        if (request.getAmount() == null || request.getAmount().signum() <= 0)
            throw new IllegalArgumentException("amount debe ser mayor a 0");
        if (request.getCurrency() == null) throw new IllegalArgumentException("currency es obligatorio");
        if (request.getReferenceType() == null) throw new IllegalArgumentException("referenceType es obligatorio");
        if (request.getReferenceId() == null || request.getReferenceId().isBlank())
            throw new IllegalArgumentException("referenceId es obligatorio");
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank())
            throw new IllegalArgumentException("idempotencyKey es obligatorio");
    }

    private void validateCompositeRequest(CreateCompositeLedgerMovementRequest request) {
        if (request == null) throw new IllegalArgumentException("El request no puede ser null");

        if (request.getDebitAccountId() == null) throw new IllegalArgumentException("debitAccountId es obligatorio");
        if (request.getCreditAccountId() == null) throw new IllegalArgumentException("creditAccountId es obligatorio");
        if (request.getDebitAccountId().equals(request.getCreditAccountId())) {
            throw new IllegalArgumentException("debitAccountId y creditAccountId no pueden ser iguales");
        }

        if (request.getAmount() == null || request.getAmount().signum() <= 0)
            throw new IllegalArgumentException("amount debe ser mayor a 0");
        if (request.getCurrency() == null) throw new IllegalArgumentException("currency es obligatorio");
        if (request.getReferenceType() == null) throw new IllegalArgumentException("referenceType es obligatorio");
        if (request.getReferenceId() == null || request.getReferenceId().isBlank())
            throw new IllegalArgumentException("referenceId es obligatorio");
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank())
            throw new IllegalArgumentException("idempotencyKey es obligatorio");
    }
}
