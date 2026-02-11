package com.banca.ledger.integration;

import com.banca.ledger.api.dto.CreateCompositeLedgerMovementRequest;
import com.banca.ledger.application.service.LedgerEntryCommandService;
import com.banca.ledger.application.service.OperationEntries;
import com.banca.ledger.domain.enums.Currency;
import com.banca.ledger.domain.enums.EntryType;
import com.banca.ledger.domain.enums.ReferenceType;
import com.banca.ledger.domain.model.LedgerEntry;
import com.banca.ledger.infrastructure.persistence.LedgerEntryRepository;
import com.banca.ledger.infrastructure.persistence.LedgerOperationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LedgerEntryCommandServiceRecordCompositeMovementIT extends BaseIT {

    @Autowired
    LedgerEntryCommandService service;

    @Autowired
    LedgerEntryRepository entryRepo;

    @Autowired
    LedgerOperationRepository opRepo;

    @Test
    void recordCompositeMovement_happyPath_shouldPersistOperationAndTwoEntries() {

        // Arrange: claves únicas por test para evitar colisiones
        String suffix = UUID.randomUUID().toString();
        String referenceId = "dep-" + suffix;
        String idempotencyKey = "idem-uc2-" + suffix;

        CreateCompositeLedgerMovementRequest request = new CreateCompositeLedgerMovementRequest();
        request.setDebitAccountId(1001L);
        request.setCreditAccountId(2001L);
        request.setAmount(new BigDecimal("150.00"));
        request.setCurrency(Currency.PEN);
        request.setReferenceType(ReferenceType.DEPOSIT);
        request.setReferenceId(referenceId);
        request.setIdempotencyKey(idempotencyKey);

        // Act
        OperationEntries result = service.recordCompositeMovement(request);

        // Assert: retorno
        assertNotNull(result);
        assertNotNull(result.operation());
        assertNotNull(result.entries());
        assertEquals(2, result.entries().size(), "Debe devolver exactamente 2 entries");

        var op = result.operation();
        assertNotNull(op.getId(), "En IT el UUID debe existir (persistencia real)");
        assertEquals(idempotencyKey, op.getIdempotencyKey());
        assertEquals(ReferenceType.DEPOSIT, op.getReferenceType());
        assertEquals(referenceId, op.getReferenceId());

        // Assert: persistencia real (DB)
        var opId = op.getId();

        assertTrue(opRepo.findById(opId).isPresent(),
                "Debe existir la operación en ledger_operations");

        List<LedgerEntry> persistedEntries = entryRepo.findByOperationId(opId);
        assertEquals(2, persistedEntries.size(),
                "Debe haber exactamente 2 entries en DB para esa operación");

        long debits = persistedEntries.stream().filter(e -> e.getEntryType() == EntryType.DEBIT).count();
        long credits = persistedEntries.stream().filter(e -> e.getEntryType() == EntryType.CREDIT).count();
        assertEquals(1, debits, "Debe existir 1 DEBIT");
        assertEquals(1, credits, "Debe existir 1 CREDIT");

        LedgerEntry debitEntry = persistedEntries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .findFirst()
                .orElseThrow();

        LedgerEntry creditEntry = persistedEntries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .findFirst()
                .orElseThrow();

        assertEquals(1001L, debitEntry.getAccountId());
        assertEquals(2001L, creditEntry.getAccountId());

        assertEquals(0, debitEntry.getAmount().compareTo(new BigDecimal("150.00")),
                "Monto debit debe ser 150.00");
        assertEquals(0, creditEntry.getAmount().compareTo(new BigDecimal("150.00")),
                "Monto credit debe ser 150.00");

        assertEquals(Currency.PEN, debitEntry.getCurrency());
        assertEquals(Currency.PEN, creditEntry.getCurrency());

        // Extra: ambas entries deben apuntar a la misma operación
        assertNotNull(debitEntry.getOperation());
        assertNotNull(creditEntry.getOperation());
        assertEquals(opId, debitEntry.getOperation().getId());
        assertEquals(opId, creditEntry.getOperation().getId());
    }

    @Test
    void recordCompositeMovement_idempotency_shouldNotDuplicate() {

        // Arrange: claves únicas por test
        String suffix = UUID.randomUUID().toString();
        String referenceId = "dep-" + suffix;
        String idempotencyKey = "idem-uc2-" + suffix;

        CreateCompositeLedgerMovementRequest request = new CreateCompositeLedgerMovementRequest();
        request.setDebitAccountId(1001L);
        request.setCreditAccountId(2001L);
        request.setAmount(new BigDecimal("150.00"));
        request.setCurrency(Currency.PEN);
        request.setReferenceType(ReferenceType.DEPOSIT);
        request.setReferenceId(referenceId);
        request.setIdempotencyKey(idempotencyKey);

        // Act: misma request 2 veces
        OperationEntries result1 = service.recordCompositeMovement(request);
        OperationEntries result2 = service.recordCompositeMovement(request);

        // Assert: ambos retornos existen
        assertNotNull(result1);
        assertNotNull(result2);

        // Assert: idempotencia => misma operación
        var opId1 = result1.operation().getId();
        var opId2 = result2.operation().getId();

        assertNotNull(opId1);
        assertEquals(opId1, opId2, "La operación debe ser la misma por idempotencia");

        // Assert: ambos deben devolver exactamente 2 entries
        assertNotNull(result1.entries());
        assertNotNull(result2.entries());
        assertEquals(2, result1.entries().size(), "Debe devolver 2 entries");
        assertEquals(2, result2.entries().size(), "Debe devolver 2 entries");

        // Assert: ids de entries deben coincidir (sin importar el orden)
        var ids1 = result1.entries().stream()
                .map(LedgerEntry::getId)
                .collect(toSet());

        var ids2 = result2.entries().stream()
                .map(LedgerEntry::getId)
                .collect(toSet());

        assertFalse(ids1.contains(null), "Las entries retornadas deben venir persistidas (id no null)");
        assertFalse(ids2.contains(null), "Las entries retornadas deben venir persistidas (id no null)");
        assertEquals(ids1, ids2, "Las mismas entries deben retornarse en el reintento (idempotencia)");

        // Assert: en DB no debe duplicar (solo 2 rows para esa operación)
        List<LedgerEntry> persisted = entryRepo.findByOperationId(opId1);
        assertEquals(2, persisted.size(), "Debe haber solo 2 entries en BD por idempotencia");

        // Extra: asegurar 1 DEBIT y 1 CREDIT
        long debits = persisted.stream().filter(e -> e.getEntryType() == EntryType.DEBIT).count();
        long credits = persisted.stream().filter(e -> e.getEntryType() == EntryType.CREDIT).count();
        assertEquals(1, debits, "Debe existir 1 DEBIT");
        assertEquals(1, credits, "Debe existir 1 CREDIT");

        // Extra: asegurar cuentas/monto/moneda coherentes
        LedgerEntry debitEntry = persisted.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .findFirst()
                .orElseThrow();

        LedgerEntry creditEntry = persisted.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .findFirst()
                .orElseThrow();

        assertEquals(1001L, debitEntry.getAccountId());
        assertEquals(2001L, creditEntry.getAccountId());

        assertEquals(0, debitEntry.getAmount().compareTo(new BigDecimal("150.00")));
        assertEquals(0, creditEntry.getAmount().compareTo(new BigDecimal("150.00")));

        assertEquals(Currency.PEN, debitEntry.getCurrency());
        assertEquals(Currency.PEN, creditEntry.getCurrency());
    }
}
