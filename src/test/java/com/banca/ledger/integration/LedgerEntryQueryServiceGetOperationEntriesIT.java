package com.banca.ledger.integration;

import com.banca.ledger.application.service.LedgerEntryQueryService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LedgerEntryQueryServiceGetOperationEntriesIT extends BaseIT {

    @Autowired
    LedgerEntryQueryService service;

    @Autowired
    LedgerEntryRepository entryRepo;

    @Autowired
    LedgerOperationRepository opRepo;

    @Test
    void getOperationEntries_happyPath_shouldReturnOperationWithEntries() {

        // Arrange: evitar colisiones
        String suffix = UUID.randomUUID().toString();

        LedgerOperation op = opRepo.save(
                new LedgerOperation(
                        "idem-opentries-" + suffix,
                        ReferenceType.DEPOSIT,
                        "ref-opentries-" + suffix
                )
        );

        Long accountId = 10L;

        LedgerEntry debit = new LedgerEntry(
                accountId,
                EntryType.DEBIT,
                new BigDecimal("50.00"),
                Currency.PEN,
                op
        );

        LedgerEntry credit = new LedgerEntry(
                accountId,
                EntryType.CREDIT,
                new BigDecimal("150.00"),
                Currency.PEN,
                op
        );

        entryRepo.saveAll(List.of(debit, credit));
        assertNotNull(op.getId());

        // Act
        OperationEntries result = service.getOperationEntries(op.getId());

        // Assert
        assertNotNull(result);
        assertNotNull(result.operation());
        assertNotNull(result.entries());

        assertEquals(op.getId(), result.operation().getId());

        List<LedgerEntry> entries = result.entries();
        assertEquals(2, entries.size());

        // 1 DEBIT + 1 CREDIT (sin depender del orden)
        LedgerEntry debitEntry = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .findFirst()
                .orElseThrow();

        LedgerEntry creditEntry = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .findFirst()
                .orElseThrow();

        // verificación DEBIT
        assertEquals(accountId, debitEntry.getAccountId());
        assertEquals(Currency.PEN, debitEntry.getCurrency());
        assertEquals(0, new BigDecimal("50.00").compareTo(debitEntry.getAmount()));

        // verificación CREDIT
        assertEquals(accountId, creditEntry.getAccountId());
        assertEquals(Currency.PEN, creditEntry.getCurrency());
        assertEquals(0, new BigDecimal("150.00").compareTo(creditEntry.getAmount()));

        // ambas entries apuntan a la operación
        assertNotNull(debitEntry.getOperation());
        assertNotNull(creditEntry.getOperation());
        assertEquals(op.getId(), debitEntry.getOperation().getId());
        assertEquals(op.getId(), creditEntry.getOperation().getId());
    }
}
