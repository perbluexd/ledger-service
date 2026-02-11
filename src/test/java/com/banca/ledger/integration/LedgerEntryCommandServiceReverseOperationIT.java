package com.banca.ledger.integration;

import com.banca.ledger.application.service.LedgerEntryCommandService;
import com.banca.ledger.application.service.OperationEntries;
import com.banca.ledger.domain.enums.Currency;
import com.banca.ledger.domain.enums.EntryType;
import com.banca.ledger.domain.enums.ReferenceType;
import com.banca.ledger.domain.model.LedgerEntry;
import com.banca.ledger.domain.model.LedgerOperation;
import com.banca.ledger.infrastructure.persistence.LedgerEntryRepository;
import com.banca.ledger.infrastructure.persistence.LedgerOperationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LedgerEntryCommandServiceReverseOperationIT extends BaseIT {

    @Autowired
    LedgerEntryCommandService service;

    @Autowired
    LedgerEntryRepository entryRepo;

    @Autowired
    LedgerOperationRepository opRepo;

    @Test
    void reverseOperation_happyPath_shouldCreateReversalEntries() {

        // Arrange: ids únicos para evitar colisiones entre tests
        String suffix = UUID.randomUUID().toString();
        String idempotencyKey = "idem-orig-" + suffix;
        String referenceId = "ref-orig-" + suffix;

        LedgerOperation originalOp = opRepo.save(
                new LedgerOperation(
                        idempotencyKey,
                        ReferenceType.DEPOSIT,
                        referenceId
                )
        );

        LedgerEntry origDebit = new LedgerEntry(
                1001L,
                EntryType.DEBIT,
                new BigDecimal("150.00"),
                Currency.PEN,
                originalOp
        );

        LedgerEntry origCredit = new LedgerEntry(
                2001L,
                EntryType.CREDIT,
                new BigDecimal("150.00"),
                Currency.PEN,
                originalOp
        );

        entryRepo.saveAll(List.of(origDebit, origCredit));

        UUID originalOpId = originalOp.getId();
        assertNotNull(originalOpId);

        // Foto del estado original
        List<LedgerEntry> originalPersisted = entryRepo.findByOperationId(originalOpId);
        assertEquals(2, originalPersisted.size(), "La operación original debe tener 2 entries");
        Set<Long> originalEntryIds = originalPersisted.stream()
                .map(LedgerEntry::getId)
                .collect(Collectors.toSet());

        // Act: revertir
        OperationEntries result = service.reverseOperation(originalOpId);

        // Assert: retorno básico
        assertNotNull(result);
        assertNotNull(result.operation());
        assertNotNull(result.entries());
        assertEquals(2, result.entries().size(), "La reversión debe crear 2 entries (invertidas)");

        // Assert: operación reversa es nueva
        UUID reversalOpId = result.operation().getId();
        assertNotNull(reversalOpId);
        assertNotEquals(originalOpId, reversalOpId, "La reversión debe crear una operación nueva");

        // Assert: operación reversa existe en BD
        assertTrue(opRepo.findById(reversalOpId).isPresent(), "La operación de reversión debe persistirse");

        // Assert: entries reversas en BD
        List<LedgerEntry> reversalPersisted = entryRepo.findByOperationId(reversalOpId);
        assertEquals(2, reversalPersisted.size(),
                "Debe haber 2 entries persistidas para la operación de reversión");

        // Assert: original no cambia
        List<LedgerEntry> originalAfter = entryRepo.findByOperationId(originalOpId);
        assertEquals(2, originalAfter.size(), "La operación original debe seguir teniendo 2 entries");
        Set<Long> originalAfterIds = originalAfter.stream()
                .map(LedgerEntry::getId)
                .collect(Collectors.toSet());
        assertEquals(originalEntryIds, originalAfterIds,
                "Las entries originales deben ser las mismas (no reemplazadas)");

        // Comparación por tipo
        LedgerEntry origD = originalAfter.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .findFirst()
                .orElseThrow();

        LedgerEntry origC = originalAfter.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .findFirst()
                .orElseThrow();

        LedgerEntry revD = reversalPersisted.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .findFirst()
                .orElseThrow();

        LedgerEntry revC = reversalPersisted.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .findFirst()
                .orElseThrow();

        // original DEBIT -> reversal CREDIT
        assertEquals(origD.getAccountId(), revC.getAccountId());
        assertEquals(0, origD.getAmount().compareTo(revC.getAmount()), "Monto debe ser igual (DEBIT->CREDIT)");
        assertEquals(origD.getCurrency(), revC.getCurrency());

        // original CREDIT -> reversal DEBIT
        assertEquals(origC.getAccountId(), revD.getAccountId());
        assertEquals(0, origC.getAmount().compareTo(revD.getAmount()), "Monto debe ser igual (CREDIT->DEBIT)");
        assertEquals(origC.getCurrency(), revD.getCurrency());

        // ambas entries reversas apuntan a la operación reversa
        assertEquals(reversalOpId, revD.getOperation().getId());
        assertEquals(reversalOpId, revC.getOperation().getId());
    }
}
