package com.banca.ledger.integration;

import com.banca.ledger.api.dto.AccountBalanceResponse;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LedgerEntryQueryServiceBalanceIT extends BaseIT {

    @Autowired
    LedgerEntryQueryService service;

    @Autowired
    LedgerEntryRepository entryRepo;

    @Autowired
    LedgerOperationRepository opRepo;

    @Test
    void getAccountBalance_happyPath_shouldReturnBalance() {

        // Arrange: evitar colisiones
        String suffix = UUID.randomUUID().toString();
        String idempotencyKey = "idem-balance-" + suffix;
        String referenceId = "ref-balance-" + suffix;

        LedgerOperation op = opRepo.save(
                new LedgerOperation(
                        idempotencyKey,
                        ReferenceType.DEPOSIT,
                        referenceId
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

        // Act
        AccountBalanceResponse result = service.getAccountBalance(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(Currency.PEN, result.getCurrency());

        // balance = credits(150) - debits(50) = 100
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getBalance()));
    }
}
