package com.banca.ledger.application.service;

import com.banca.ledger.api.mapper.CompositeMovementAssembler;
import com.banca.ledger.application.exception.NotFoundException;
import com.banca.ledger.domain.enums.Currency;
import com.banca.ledger.domain.enums.EntryType;
import com.banca.ledger.domain.enums.ReferenceType;
import com.banca.ledger.domain.model.LedgerEntry;
import com.banca.ledger.domain.model.LedgerOperation;
import com.banca.ledger.infrastructure.persistence.LedgerEntryRepository;
import com.banca.ledger.infrastructure.persistence.LedgerOperationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerEntryCommandServiceReverseOperationTest {

    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private LedgerOperationRepository ledgerOperationRepository;
    @Mock private CompositeMovementAssembler compositeMovementAssembler; // no se usa aquí pero el ctor lo pide

    @InjectMocks private LedgerEntryCommandService service;

    @Test
    void happyPath_createsReversalOperation_andInvertsEntryTypes() {
        // Arrange: operación original
        UUID originalOpId = UUID.randomUUID();

        LedgerOperation originalOp = new LedgerOperation(
                "idem-uc2-abc-123",
                ReferenceType.DEPOSIT,
                "ref-123"
        );
        setPrivateField(originalOp, "id", originalOpId);

        when(ledgerOperationRepository.findById(originalOpId))
                .thenReturn(Optional.of(originalOp));

        // Entries originales: 1 DEBIT + 1 CREDIT
        LedgerEntry originalDebit = new LedgerEntry(
                10L, EntryType.DEBIT, new BigDecimal("100.00"), Currency.PEN, originalOp
        );
        LedgerEntry originalCredit = new LedgerEntry(
                20L, EntryType.CREDIT, new BigDecimal("100.00"), Currency.PEN, originalOp
        );

        when(ledgerEntryRepository.findByOperationId(originalOpId))
                .thenReturn(List.of(originalDebit, originalCredit));

        // Al guardar la operación de reversión, devolvemos una "persistida"
        LedgerOperation reversalOp = new LedgerOperation(
                "reversal:" + originalOpId,
                originalOp.getReferenceType(),
                originalOp.getReferenceId()
        );
        UUID reversalOpId = UUID.randomUUID();
        setPrivateField(reversalOp, "id", reversalOpId);

        when(ledgerOperationRepository.save(any(LedgerOperation.class)))
                .thenReturn(reversalOp);

        // saveAll devuelve lo mismo que recibe (simulación)
        when(ledgerEntryRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        OperationEntries result = service.reverseOperation(originalOpId);

        // Assert: operación nueva
        assertNotNull(result);
        assertNotNull(result.operation());
        assertNotEquals(originalOpId, result.operation().getId(), "Debe crear una NUEVA operación");
        assertEquals("reversal:" + originalOpId, result.operation().getIdempotencyKey());

        // Assert: 2 entries con types invertidos
        assertNotNull(result.entries());
        assertEquals(2, result.entries().size());

        long debits = result.entries().stream().filter(e -> e.getEntryType() == EntryType.DEBIT).count();
        long credits = result.entries().stream().filter(e -> e.getEntryType() == EntryType.CREDIT).count();
        assertEquals(1, debits);
        assertEquals(1, credits);

        // Bonus: validar que apuntan a la reversalOp
        assertTrue(result.entries().stream().allMatch(e -> e.getOperation() == reversalOp));

        // Verify: llamadas correctas
        verify(ledgerOperationRepository).findById(originalOpId);
        verify(ledgerEntryRepository).findByOperationId(originalOpId);
        verify(ledgerOperationRepository).save(any(LedgerOperation.class));
        verify(ledgerEntryRepository).saveAll(anyList());
    }

    @Test
    void reverseOperation_nullId_throwsException() {

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.reverseOperation(null)
        );

        assertEquals("operationId no puede ser null", exception.getMessage());

        // opcional: asegurar que NO se llamó a repositorios
        verifyNoInteractions(ledgerOperationRepository);
        verifyNoInteractions(ledgerEntryRepository);
    }

    @Test
    void reverseOperation_notFound_throwsNotFound() {
        UUID opId = UUID.randomUUID();

        when(ledgerOperationRepository.findById(opId))
                .thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                service.reverseOperation(opId)
        );

        assertEquals("Operación no encontrada: " + opId, ex.getMessage());

        verify(ledgerOperationRepository).findById(opId);
        verifyNoInteractions(ledgerEntryRepository);
        verifyNoMoreInteractions(ledgerOperationRepository);
    }
    @Test
    void reverseOperation_noEntries_throwsIllegalState() {
        UUID originalOpId = UUID.randomUUID();

        LedgerOperation originalOp = new LedgerOperation(
                "idem-uc2-abc-123",
                ReferenceType.DEPOSIT,
                "ref-123"
        );
        setPrivateField(originalOp, "id", originalOpId);

        when(ledgerOperationRepository.findById(originalOpId))
                .thenReturn(Optional.of(originalOp));

        when(ledgerEntryRepository.findByOperationId(originalOp.getId()))
                .thenReturn(Collections.emptyList());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.reverseOperation(originalOpId)
        );

        assertEquals("No se encontraron asientos para la operación: " + originalOpId, ex.getMessage());

        verify(ledgerOperationRepository).findById(originalOpId);
        verify(ledgerEntryRepository).findByOperationId(originalOpId);

        verify(ledgerOperationRepository, never()).save(any(LedgerOperation.class));
        verify(ledgerEntryRepository, never()).saveAll(anyList());
    }





    private static void setPrivateField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("No se pudo setear el campo '" + fieldName + "' por reflexión", e);
        }
    }
}
