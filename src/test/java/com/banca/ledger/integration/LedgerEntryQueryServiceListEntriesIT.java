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
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LedgerEntryQueryServiceListEntriesIT extends BaseIT {

    @Autowired
    LedgerEntryQueryService service;

    @Autowired
    LedgerEntryRepository entryRepo;

    @Autowired
    LedgerOperationRepository opRepo;

    @Test
    void listEntries_happyPath_shouldReturnPageOrderedDesc() {
        // Arrange (keys únicas para evitar colisiones)
        String suffix = UUID.randomUUID().toString();

        LedgerOperation op = opRepo.save(
                new LedgerOperation(
                        "idem-list-" + suffix,
                        ReferenceType.DEPOSIT,
                        "ref-list-" + suffix
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

        int page = 0;
        int size = 10;

        // Act
        Page<LedgerEntry> result = service.listEntries(accountId, page, size);

        // Assert: Page básica
        assertNotNull(result);
        assertEquals(page, result.getNumber(), "Debe devolver la página solicitada");
        assertEquals(size, result.getSize(), "Debe respetar el size solicitado");
        assertEquals(2, result.getNumberOfElements(), "En esta página deben venir 2 elementos");
        assertEquals(2, result.getContent().size(), "Contenido debe tener 2 elementos");

        // Assert: filtro por accountId
        assertTrue(
                result.getContent().stream().allMatch(e -> accountId.equals(e.getAccountId())),
                "Todas las entries deben pertenecer al accountId solicitado"
        );

        // Assert: orden determinista DESC por (createdAt DESC, id DESC)
        List<LedgerEntry> content = result.getContent();

        // sanity checks
        assertTrue(content.stream().allMatch(e -> e.getCreatedAt() != null), "createdAt no debe ser null");
        assertTrue(content.stream().allMatch(e -> e.getId() != null), "id no debe ser null");

        Comparator<LedgerEntry> expectedOrder = (a, b) -> {
            Instant ca = a.getCreatedAt();
            Instant cb = b.getCreatedAt();
            int cmp = cb.compareTo(ca); // DESC createdAt
            if (cmp != 0) return cmp;

            // desempate por id DESC (asumiendo Long)
            // si tu id no es Long, ajusta este compare
            return Long.compare(b.getId(), a.getId());
        };

        List<LedgerEntry> sorted = content.stream().sorted(expectedOrder).toList();
        assertEquals(sorted, content,
                "Debe venir ordenado DESC por createdAt y, si empata, por id DESC (orden estable)");
    }
}
