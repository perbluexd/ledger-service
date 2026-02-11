package com.banca.ledger.application.service;

import com.banca.ledger.api.dto.CreateLedgerEntryRequest;
import com.banca.ledger.api.mapper.CompositeMovementAssembler;
import com.banca.ledger.domain.enums.Currency;
import com.banca.ledger.domain.enums.EntryType;
import com.banca.ledger.domain.enums.ReferenceType;
import com.banca.ledger.domain.model.LedgerEntry;
import com.banca.ledger.domain.model.LedgerOperation;
import com.banca.ledger.infrastructure.persistence.LedgerEntryRepository;
import com.banca.ledger.infrastructure.persistence.LedgerOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LedgerEntryCommandServiceCreateEntryTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private LedgerOperationRepository ledgerOperationRepository;

    @Mock
    private CompositeMovementAssembler compositeMovementAssembler;

    @InjectMocks
    private LedgerEntryCommandService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void newOperation_createsEntry() {
        // 1) Arrange
        CreateLedgerEntryRequest request = new CreateLedgerEntryRequest();
        request.setAccountId(10L);
        request.setEntryType(EntryType.CREDIT);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency(Currency.PEN);
        request.setReferenceType(ReferenceType.DEPOSIT);
        request.setReferenceId("ref-123");
        request.setIdempotencyKey("idem-abc-123");

        // Operación NO existe
        when(ledgerOperationRepository.findByIdempotencyKey("idem-abc-123"))
                .thenReturn(Optional.empty());

        // Al guardar operación, devolvemos una operación "persistida"
        LedgerOperation savedOperation = new LedgerOperation(
                "idem-abc-123",
                ReferenceType.DEPOSIT,
                "ref-123"
        );

        // 🔧 IMPORTANTE: en unit tests con mocks NO corre @PrePersist,
        // así que seteamos manualmente el UUID para simular "persistencia"
        UUID opId = UUID.randomUUID();
        setPrivateField(savedOperation, "id", opId);

        when(ledgerOperationRepository.save(any(LedgerOperation.class)))
                .thenReturn(savedOperation);

        // Aún no hay entries asociados a esa operación
        when(ledgerEntryRepository.findByOperationId(opId))
                .thenReturn(Collections.emptyList());

        // Al guardar entry, devolvemos el mismo objeto (simulación)
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 2) Act
        LedgerEntry result = service.createEntry(request);

        // 3) Assert (sobre el retorno del service)
        assertNotNull(result);
        assertEquals(10L, result.getAccountId());
        assertEquals(EntryType.CREDIT, result.getEntryType());
        assertEquals(new BigDecimal("100.00"), result.getAmount());
        assertEquals(Currency.PEN, result.getCurrency());
        assertNotNull(result.getOperation());
        assertEquals("idem-abc-123", result.getOperation().getIdempotencyKey());
        assertEquals(opId, result.getOperation().getId());

        // 4) Verify (interacciones)
        verify(ledgerOperationRepository, times(1)).findByIdempotencyKey("idem-abc-123");
        verify(ledgerOperationRepository, times(1)).save(any(LedgerOperation.class));
        verify(ledgerEntryRepository, times(1)).findByOperationId(opId);
        verify(ledgerEntryRepository, times(1)).save(any(LedgerEntry.class));

        // 5) Capturar lo que se guardó para verificar contenido
        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(entryCaptor.capture());

        LedgerEntry savedEntry = entryCaptor.getValue();
        assertEquals(10L, savedEntry.getAccountId());
        assertEquals(EntryType.CREDIT, savedEntry.getEntryType());
        assertEquals(new BigDecimal("100.00"), savedEntry.getAmount());
        assertEquals(Currency.PEN, savedEntry.getCurrency());
        assertNotNull(savedEntry.getOperation());
        assertEquals("idem-abc-123", savedEntry.getOperation().getIdempotencyKey());
        assertEquals(opId, savedEntry.getOperation().getId());
    }

    @Test
    void existingEntry_returnsExisting() {
        // 1) Arrange
        CreateLedgerEntryRequest request = new CreateLedgerEntryRequest();
        request.setAccountId(10L);
        request.setEntryType(EntryType.CREDIT);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency(Currency.PEN);
        request.setReferenceType(ReferenceType.DEPOSIT);
        request.setReferenceId("ref-123");
        request.setIdempotencyKey("idem-abc-123");

        // Operación YA existe (o se recupera)
        LedgerOperation existingOperation = new LedgerOperation(
                "idem-abc-123",
                ReferenceType.DEPOSIT,
                "ref-123"
        );
        UUID opId = UUID.randomUUID();
        setPrivateField(existingOperation, "id", opId);

        when(ledgerOperationRepository.findByIdempotencyKey("idem-abc-123"))
                .thenReturn(Optional.of(existingOperation));

        // Ya existe EXACTAMENTE 1 entry asociada a la operación
        LedgerEntry existingEntry = new LedgerEntry(
                10L,
                EntryType.CREDIT,
                new BigDecimal("100.00"),
                Currency.PEN,
                existingOperation
        );

        when(ledgerEntryRepository.findByOperationId(opId))
                .thenReturn(Collections.singletonList(existingEntry));

        // 2) Act
        LedgerEntry result = service.createEntry(request);

        // 3) Assert
        assertNotNull(result);
        assertSame(existingEntry, result, "Debe devolver la MISMA instancia existente (idempotencia)");

        // 4) Verify (interacciones)
        verify(ledgerOperationRepository, times(1)).findByIdempotencyKey("idem-abc-123");
        verify(ledgerOperationRepository, never()).save(any(LedgerOperation.class));

        verify(ledgerEntryRepository, times(1)).findByOperationId(opId);
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void moreThanOneEntry_throwsIllegalState() {
        // 1) Arrange
        CreateLedgerEntryRequest request = new CreateLedgerEntryRequest();
        request.setAccountId(10L);
        request.setEntryType(EntryType.CREDIT);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency(Currency.PEN);
        request.setReferenceType(ReferenceType.DEPOSIT);
        request.setReferenceId("ref-123");
        request.setIdempotencyKey("idem-abc-123");

        // La operación YA existe (y debe tener un id no-null porque se usa operation.getId())
        LedgerOperation existingOperation = new LedgerOperation(
                "idem-abc-123",
                ReferenceType.DEPOSIT,
                "ref-123"
        );
        UUID opId = UUID.randomUUID();
        setPrivateField(existingOperation, "id", opId);

        when(ledgerOperationRepository.findByIdempotencyKey("idem-abc-123"))
                .thenReturn(Optional.of(existingOperation));

        // La operación tiene MÁS DE 1 entry asociado -> inconsistencia
        LedgerEntry entry1 = new LedgerEntry(
                request.getAccountId(),
                request.getEntryType(),
                request.getAmount(),
                request.getCurrency(),
                existingOperation
        );
        LedgerEntry entry2 = new LedgerEntry(
                request.getAccountId(),
                request.getEntryType(),
                request.getAmount(),
                request.getCurrency(),
                existingOperation
        );

        when(ledgerEntryRepository.findByOperationId(opId))
                .thenReturn(List.of(entry1, entry2));

        // 2) Act + Assert (esperamos excepción)
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.createEntry(request)
        );
        assertTrue(ex.getMessage().contains("Inconsistencia"));

        // 3) Verify (no debe intentar guardar nada nuevo)
        verify(ledgerOperationRepository, times(1)).findByIdempotencyKey("idem-abc-123");
        verify(ledgerOperationRepository, never()).save(any(LedgerOperation.class));

        verify(ledgerEntryRepository, times(1)).findByOperationId(opId);
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }


    /**
     * Helper para setear campos privados (como el UUID id de entidades JPA)
     * en tests unitarios, simulando el efecto de @PrePersist.
     */
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
