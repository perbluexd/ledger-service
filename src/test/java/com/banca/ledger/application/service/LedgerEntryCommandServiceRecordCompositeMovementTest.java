package com.banca.ledger.application.service;

import com.banca.ledger.api.dto.CreateCompositeLedgerMovementRequest;
import com.banca.ledger.api.mapper.CompositeMovementAssembler;
import com.banca.ledger.application.exception.ConflictException;
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
class LedgerEntryCommandServiceRecordCompositeMovementTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private LedgerOperationRepository ledgerOperationRepository;

    @Mock
    private CompositeMovementAssembler compositeMovementAssembler;

    @InjectMocks
    private LedgerEntryCommandService service;

    @Test
    void recordCompositeMovement_newOperation_createsTwoEntries() {
        // Arrange
        CreateCompositeLedgerMovementRequest request = new CreateCompositeLedgerMovementRequest();
        request.setDebitAccountId(10L);
        request.setCreditAccountId(20L);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency(Currency.PEN);
        request.setReferenceType(ReferenceType.DEPOSIT);
        request.setReferenceId("ref-123");
        request.setIdempotencyKey("idem-uc2-abc-123");

        when(ledgerOperationRepository.findByIdempotencyKey("idem-uc2-abc-123"))
                .thenReturn(Optional.empty());

        LedgerOperation savedOperation = new LedgerOperation(
                "idem-uc2-abc-123",
                ReferenceType.DEPOSIT,
                "ref-123"
        );
        UUID opId = UUID.randomUUID();
        setPrivateField(savedOperation, "id", opId);

        when(ledgerOperationRepository.save(any(LedgerOperation.class)))
                .thenReturn(savedOperation);

        when(ledgerEntryRepository.findByOperationId(opId))
                .thenReturn(Collections.emptyList());

        LedgerEntry debitEntry = new LedgerEntry(
                request.getDebitAccountId(),
                EntryType.DEBIT,
                request.getAmount(),
                request.getCurrency(),
                savedOperation
        );

        LedgerEntry creditEntry = new LedgerEntry(
                request.getCreditAccountId(),
                EntryType.CREDIT,
                request.getAmount(),
                request.getCurrency(),
                savedOperation
        );

        when(compositeMovementAssembler.toEntries(request, savedOperation))
                .thenReturn(List.of(debitEntry, creditEntry));

        when(ledgerEntryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        OperationEntries result = service.recordCompositeMovement(request);

        // Assert
        assertNotNull(result);
        assertNotNull(result.operation());
        assertEquals(opId, result.operation().getId());
        assertEquals("idem-uc2-abc-123", result.operation().getIdempotencyKey());

        assertNotNull(result.entries());
        assertEquals(2, result.entries().size());

        // Verify
        verify(ledgerOperationRepository).findByIdempotencyKey("idem-uc2-abc-123");
        verify(ledgerOperationRepository).save(any(LedgerOperation.class));
        verify(ledgerEntryRepository).findByOperationId(opId);

        verify(compositeMovementAssembler).toEntries(request, savedOperation);
        verify(ledgerEntryRepository).saveAll(anyList());

        // Captor (opcional)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LedgerEntry>> listCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(ledgerEntryRepository).saveAll(listCaptor.capture());

        List<LedgerEntry> sentToSaveAll = listCaptor.getValue();
        assertEquals(2, sentToSaveAll.size());
    }

    @Test
    void recordCompositeMovement_existingEntries_returnsExisting() {
        // Arrange
        CreateCompositeLedgerMovementRequest request = new CreateCompositeLedgerMovementRequest();
        request.setDebitAccountId(10L);
        request.setCreditAccountId(20L);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency(Currency.PEN);
        request.setReferenceType(ReferenceType.DEPOSIT);
        request.setReferenceId("ref-123");
        request.setIdempotencyKey("idem-uc2-abc-123");

        LedgerOperation existingOperation = new LedgerOperation(
                "idem-uc2-abc-123",
                ReferenceType.DEPOSIT,
                "ref-123"
        );
        UUID opId = UUID.randomUUID();
        setPrivateField(existingOperation, "id", opId);

        when(ledgerOperationRepository.findByIdempotencyKey("idem-uc2-abc-123"))
                .thenReturn(Optional.of(existingOperation));

        LedgerEntry debitEntry = new LedgerEntry(
                request.getDebitAccountId(),
                EntryType.DEBIT,
                request.getAmount(),
                request.getCurrency(),
                existingOperation
        );

        LedgerEntry creditEntry = new LedgerEntry(
                request.getCreditAccountId(),
                EntryType.CREDIT,
                request.getAmount(),
                request.getCurrency(),
                existingOperation
        );

        List<LedgerEntry> existingEntries = List.of(debitEntry, creditEntry);

        when(ledgerEntryRepository.findByOperationId(opId))
                .thenReturn(existingEntries);

        // Act
        OperationEntries result = service.recordCompositeMovement(request);

        // Assert
        assertNotNull(result);
        assertNotNull(result.operation());
        assertEquals(opId, result.operation().getId());
        assertEquals("idem-uc2-abc-123", result.operation().getIdempotencyKey());

        assertNotNull(result.entries());
        assertSame(existingEntries, result.entries(), "Debe devolver la MISMA lista existente (idempotencia)");
        assertEquals(2, result.entries().size());

        // Verify
        verify(ledgerOperationRepository).findByIdempotencyKey("idem-uc2-abc-123");
        verify(ledgerOperationRepository, never()).save(any(LedgerOperation.class));

        verify(ledgerEntryRepository).findByOperationId(opId);
        verify(compositeMovementAssembler, never()).toEntries(any(), any());
        verify(ledgerEntryRepository, never()).saveAll(anyList());
    }

    @Test
    void recordCompositeMovement_referenceMismatch_throwsConflict() {
        // Arrange
        CreateCompositeLedgerMovementRequest request = new CreateCompositeLedgerMovementRequest();
        request.setDebitAccountId(10L);
        request.setCreditAccountId(20L);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency(Currency.PEN);
        request.setReferenceType(ReferenceType.DEPOSIT);
        request.setReferenceId("ref-123");
        request.setIdempotencyKey("idem-uc2-abc-123");

        LedgerOperation existingOperation = new LedgerOperation(
                "idem-uc2-abc-123",
                ReferenceType.WITHDRAWAL,
                "ref-999"
        );
        UUID opId = UUID.randomUUID();
        setPrivateField(existingOperation, "id", opId);

        when(ledgerOperationRepository.findByIdempotencyKey("idem-uc2-abc-123"))
                .thenReturn(Optional.of(existingOperation));

        // Act + Assert
        assertThrows(ConflictException.class, () -> service.recordCompositeMovement(request));

        // Verify
        verify(ledgerOperationRepository).findByIdempotencyKey("idem-uc2-abc-123");
        verify(ledgerOperationRepository, never()).save(any(LedgerOperation.class));

        verify(ledgerEntryRepository, never()).findByOperationId(any(UUID.class));
        verifyNoInteractions(compositeMovementAssembler);
        verify(ledgerEntryRepository, never()).saveAll(anyList());
    }
    @Test
    void existingEntriesSizeNotTwo_throwsIllegalState(){
        CreateCompositeLedgerMovementRequest request = new CreateCompositeLedgerMovementRequest();
        request.setDebitAccountId(10L);
        request.setCreditAccountId(20L);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency(Currency.PEN);
        request.setReferenceType(ReferenceType.DEPOSIT);
        request.setReferenceId("ref-123");
        request.setIdempotencyKey("idem-uc2-abc-123");
        LedgerOperation existingOperation = new LedgerOperation(
                "idem-uc2-abc-123",
                ReferenceType.DEPOSIT,
                "ref-123"
        );
        UUID opId = UUID.randomUUID();
        setPrivateField(existingOperation, "id", opId);

        when(ledgerOperationRepository.findByIdempotencyKey("idem-uc2-abc-123"))
                .thenReturn(Optional.of(existingOperation));

        LedgerEntry debitEntry = new LedgerEntry(
                request.getDebitAccountId(),
                EntryType.DEBIT,
                request.getAmount(),
                request.getCurrency(),
                existingOperation
        );

        when(ledgerEntryRepository.findByOperationId(opId))
                .thenReturn(List.of(debitEntry));

        assertThrows(IllegalStateException.class, () -> service.recordCompositeMovement(request));

        verify(ledgerEntryRepository,never()).saveAll(anyList());




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
