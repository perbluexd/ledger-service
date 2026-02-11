package com.banca.ledger.integration;

import com.banca.ledger.api.dto.CreateLedgerEntryRequest;
import com.banca.ledger.application.service.LedgerEntryCommandService;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LedgerEntryCommandServiceCreateEntryIT extends BaseIT {

    @Autowired
    LedgerEntryCommandService service;

    @Autowired
    LedgerEntryRepository entryRepo;

    @Autowired
    LedgerOperationRepository opRepo;

    @Test
    void createEntry_happyPath_persisteOperacionYUnEntry() {
        // Arrange: claves únicas por test para evitar colisiones
        String suffix = UUID.randomUUID().toString();
        String referenceId = "ref-uc1-" + suffix;
        String idempotencyKey = "idem-uc1-" + suffix;

        CreateLedgerEntryRequest req = new CreateLedgerEntryRequest();
        req.setAccountId(10L);
        req.setEntryType(EntryType.CREDIT);
        req.setAmount(new BigDecimal("100.00"));
        req.setCurrency(Currency.PEN);

        req.setReferenceType(ReferenceType.DEPOSIT);
        req.setReferenceId(referenceId);
        req.setIdempotencyKey(idempotencyKey);

        // Act
        LedgerEntry saved = service.createEntry(req);

        // Assert 1: retorno coherente
        assertNotNull(saved);
        assertNotNull(saved.getId(), "Debe tener ID asignado al persistir");
        assertEquals(10L, saved.getAccountId());
        assertEquals(EntryType.CREDIT, saved.getEntryType());
        assertEquals(0, saved.getAmount().compareTo(new BigDecimal("100.00")), "Monto debe ser 100.00");
        assertEquals(Currency.PEN, saved.getCurrency());

        // Assert 2: operación asociada
        assertNotNull(saved.getOperation(), "El entry debe tener operation asociada");
        assertNotNull(saved.getOperation().getId(), "La operation debe tener UUID");

        // Assert 3: persistencia real en DB
        assertTrue(entryRepo.findById(saved.getId()).isPresent(),
                "Debe existir la fila en ledger_entries");

        assertTrue(opRepo.findById(saved.getOperation().getId()).isPresent(),
                "Debe existir la fila en ledger_operations");
    }

    @Test
    void createEntry_idempotency_sameKey_shouldNotDuplicateEntry() {
        // Arrange: claves únicas por test
        String suffix = UUID.randomUUID().toString();
        String referenceId = "ref-uc1-" + suffix;
        String idempotencyKey = "idem-uc1-" + suffix;

        CreateLedgerEntryRequest req = new CreateLedgerEntryRequest();
        req.setAccountId(10L);
        req.setEntryType(EntryType.CREDIT);
        req.setAmount(new BigDecimal("100.00"));
        req.setCurrency(Currency.PEN);

        req.setReferenceType(ReferenceType.DEPOSIT);
        req.setReferenceId(referenceId);
        req.setIdempotencyKey(idempotencyKey); // misma key en ambas llamadas

        // Act (1)
        LedgerEntry first = service.createEntry(req);

        // Act (2)
        LedgerEntry second = service.createEntry(req);

        // Assert 1: misma entry
        assertNotNull(first.getId());
        assertNotNull(second.getId());
        assertEquals(first.getId(), second.getId(),
                "Debe devolver la misma entry si se reintenta con la misma key");

        // Assert 2: en DB solo existe 1 entry para esa operación
        var opId = first.getOperation().getId();
        List<LedgerEntry> entries = entryRepo.findByOperationId(opId);
        assertEquals(1, entries.size(),
                "No debe duplicar entries; debe haber exactamente 1 entry para la operación");

        // Assert 3: existe 1 operación con esa idempotencyKey (repo devuelve Optional)
        assertTrue(opRepo.findByIdempotencyKey(idempotencyKey).isPresent(),
                "Debe existir la operación por idempotencyKey");
    }
}
