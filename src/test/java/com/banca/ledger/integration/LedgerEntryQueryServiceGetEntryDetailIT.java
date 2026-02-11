package com.banca.ledger.integration;

import com.banca.ledger.application.service.LedgerEntryQueryService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LedgerEntryQueryServiceGetEntryDetailIT extends BaseIT {

    @Autowired
    LedgerEntryQueryService service;

    @Autowired
    LedgerEntryRepository entryRepo;

    @Autowired
    LedgerOperationRepository opRepo;

    @Test
    void getEntryDetail_happyPath_shouldReturnDetail() {

        // Arrange: evitar colisiones
        String suffix = UUID.randomUUID().toString();
        LedgerOperation op = opRepo.save(
                new LedgerOperation(
                        "idem-entrydetail-" + suffix,
                        ReferenceType.DEPOSIT,
                        "ref-entrydetail-" + suffix
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

        LedgerEntry saved = entryRepo.save(debit);
        assertNotNull(saved.getId());

        // Act
        LedgerEntry result = service.getEntryDetail(saved.getId());

        // Assert
        assertNotNull(result);

        assertEquals(saved.getAccountId(), result.getAccountId());
        assertEquals(saved.getEntryType(), result.getEntryType());
        assertEquals(0, saved.getAmount().compareTo(result.getAmount()), "amount debe coincidir");
        assertEquals(saved.getCurrency(), result.getCurrency());

        // "detail": operation cargada (EntityGraph / join fetch)
        assertNotNull(result.getOperation());
        assertEquals(op.getId(), result.getOperation().getId());
    }
}
