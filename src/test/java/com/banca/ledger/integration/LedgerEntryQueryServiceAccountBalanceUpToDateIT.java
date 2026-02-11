package com.banca.ledger.integration;

import com.banca.ledger.api.dto.AccountBalanceResponse;
import com.banca.ledger.application.service.LedgerEntryQueryService;
import com.banca.ledger.domain.enums.Currency;
import com.banca.ledger.domain.enums.EntryType;
import com.banca.ledger.domain.enums.ReferenceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LedgerEntryQueryServiceAccountBalanceUpToDateIT extends BaseIT {

    @Autowired
    private LedgerEntryQueryService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void getAccountBalanceUpToDate_happyPath_shouldFilterByDate() {
        // Arrange
        Long accountId = 10L;

        Instant t1 = Instant.parse("2026-02-03T10:00:00Z"); // BEFORE
        Instant t2 = Instant.parse("2026-02-03T10:10:00Z"); // BEFORE
        Instant cut = Instant.parse("2026-02-03T10:30:00Z"); // upToDate
        Instant t3 = Instant.parse("2026-02-03T11:00:00Z"); // AFTER (no debe contar)

        // Claves únicas por test (evita colisiones en unique constraints)
        String suffix = UUID.randomUUID().toString();

        UUID op1 = UUID.randomUUID();
        UUID op2 = UUID.randomUUID();
        UUID op3 = UUID.randomUUID();

        // Insert ledger_operations
        jdbc.update("""
                INSERT INTO ledger_operations (id, idempotency_key, reference_type, reference_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                op1, "idem-up-001-" + suffix, ReferenceType.DEPOSIT.name(), "ref-up-001-" + suffix, Timestamp.from(t1)
        );

        jdbc.update("""
                INSERT INTO ledger_operations (id, idempotency_key, reference_type, reference_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                op2, "idem-up-002-" + suffix, ReferenceType.DEPOSIT.name(), "ref-up-002-" + suffix, Timestamp.from(t2)
        );

        jdbc.update("""
                INSERT INTO ledger_operations (id, idempotency_key, reference_type, reference_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                op3, "idem-up-003-" + suffix, ReferenceType.DEPOSIT.name(), "ref-up-003-" + suffix, Timestamp.from(t3)
        );

        // Insert ledger_entries
        jdbc.update("""
                INSERT INTO ledger_entries (account_id, entry_type, amount, currency, created_at, operation_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                accountId, EntryType.CREDIT.name(), new BigDecimal("150.00"),
                Currency.PEN.name(), Timestamp.from(t1), op1
        );

        jdbc.update("""
                INSERT INTO ledger_entries (account_id, entry_type, amount, currency, created_at, operation_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                accountId, EntryType.DEBIT.name(), new BigDecimal("50.00"),
                Currency.PEN.name(), Timestamp.from(t2), op2
        );

        jdbc.update("""
                INSERT INTO ledger_entries (account_id, entry_type, amount, currency, created_at, operation_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                accountId, EntryType.CREDIT.name(), new BigDecimal("999.00"),
                Currency.PEN.name(), Timestamp.from(t3), op3
        );

        // Act
        AccountBalanceResponse result = service.getAccountBalanceUpToDate(accountId, cut);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(Currency.PEN, result.getCurrency());

        assertEquals(0, new BigDecimal("100.00").compareTo(result.getBalance()));
    }
}
