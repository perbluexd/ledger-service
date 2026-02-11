package com.banca.ledger.application.service;

import com.banca.ledger.api.dto.AccountBalanceResponse;
import com.banca.ledger.application.exception.NotFoundException;
import com.banca.ledger.domain.enums.Currency;
import com.banca.ledger.domain.enums.EntryType;
import com.banca.ledger.domain.model.LedgerEntry;
import com.banca.ledger.domain.model.LedgerOperation;
import com.banca.ledger.infrastructure.persistence.LedgerEntryRepository;
import com.banca.ledger.infrastructure.persistence.LedgerOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class LedgerEntryQueryServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private LedgerOperationRepository ledgerOperationRepository;

    @InjectMocks
    private LedgerEntryQueryService ledgerEntryQueryService;

    @Test
    void listEntries_happyPath_shouldReturnEntriesPage() {
        // Arrange
        Long accountId = 10L;
        int page = 0;
        int size = 5;

        LedgerEntry e1 = mock(LedgerEntry.class);
        LedgerEntry e2 = mock(LedgerEntry.class);

        Page<LedgerEntry> expectedPage = new PageImpl<>(List.of(e1, e2));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(eq(accountId), pageableCaptor.capture()))
                .thenReturn(expectedPage);

        // Act
        Page<LedgerEntry> result = ledgerEntryQueryService.listEntries(accountId, page, size);

        // Assert
        assertNotNull(result);
        assertSame(expectedPage, result);
        assertEquals(2, result.getContent().size());

        Pageable pageableUsed = pageableCaptor.getValue();
        assertEquals(page, pageableUsed.getPageNumber());
        assertEquals(size, pageableUsed.getPageSize());

        verify(ledgerEntryRepository, times(1))
                .findByAccountIdOrderByCreatedAtDesc(eq(accountId), any(Pageable.class));

        verifyNoInteractions(ledgerOperationRepository);
        verifyNoMoreInteractions(ledgerEntryRepository);
    }
    @Test
    void listEntries_accountIdNull_shouldThrow() {
        // Act + Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                ledgerEntryQueryService.listEntries(null, 0, 10)
        );

        assertEquals("accountId inválido", exception.getMessage());

        verifyNoInteractions(ledgerOperationRepository);
        verifyNoInteractions(ledgerEntryRepository);
    }

    @Test
    void listEntries_pageNegative_shouldThrow(){
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                ledgerEntryQueryService.listEntries(10L, -1, 10)
        );
        assertEquals("page no puede ser negativo", exception.getMessage());
        verifyNoInteractions(ledgerEntryRepository);
        verifyNoInteractions(ledgerOperationRepository);
    }
    @Test
    void listEntries_sizeZero_shouldThrow(){
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                ledgerEntryQueryService.listEntries(10L, 0, 0)
        );
        assertEquals("size debe estar entre 1 y 100", exception.getMessage());
        verifyNoInteractions(ledgerEntryRepository);
        verifyNoInteractions(ledgerOperationRepository);
    }

    // Tests del método 2 DEl LedgerEntryQueryService: getEntryDetail

    @Test
    void getEntryDetail_happyPath_shouldReturnEntry() {
        // arrange
        Long entryId = 1L;
        LedgerEntry expectedEntry = mock(LedgerEntry.class);

        when(ledgerEntryRepository.findDetailById(entryId))
                .thenReturn(Optional.of(expectedEntry));

        // act
        LedgerEntry result = ledgerEntryQueryService.getEntryDetail(entryId);

        // assert
        assertNotNull(result);
        assertSame(expectedEntry, result);

        verify(ledgerEntryRepository, times(1)).findDetailById(entryId);
        verifyNoInteractions(ledgerOperationRepository);
        verifyNoMoreInteractions(ledgerEntryRepository);
    }

    @Test
    void getEntryDetail_entryIdNull_shouldThrow(){
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                ledgerEntryQueryService.getEntryDetail(null)
        );
        assertEquals("entryId inválido", ex.getMessage());
        verifyNoInteractions(ledgerEntryRepository);
        verifyNoInteractions(ledgerOperationRepository);

    }
    @Test
    void getEntryDetail_notFound_shouldthrow(){
        Long entryId = 99L;
        when(ledgerEntryRepository.findDetailById(entryId)).thenReturn(Optional.empty());
        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                ledgerEntryQueryService.getEntryDetail(entryId)
        );
        assertEquals("Asiento contable no encontrado para el id: " + entryId, ex.getMessage());
        verify(ledgerEntryRepository, times(1)).findDetailById(entryId);
        verifyNoInteractions(ledgerOperationRepository);

    }

    // Test del método 3 getOperationEntries
    @Test
    void getOperationEntries_happyPath_shouldReturnOperationEntries() {
        UUID operationId = UUID.randomUUID();

        LedgerOperation op = mock(LedgerOperation.class);
        when(op.getId()).thenReturn(operationId);

        LedgerEntry e1 = mock(LedgerEntry.class);
        LedgerEntry e2 = mock(LedgerEntry.class);
        List<LedgerEntry> entries = List.of(e1, e2);

        when(ledgerOperationRepository.findById(operationId))
                .thenReturn(Optional.of(op));
        when(ledgerEntryRepository.findByOperationId(operationId))
                .thenReturn(entries);

        OperationEntries result = ledgerEntryQueryService.getOperationEntries(operationId);

        assertNotNull(result);
        assertSame(op, result.operation());
        assertSame(entries, result.entries());

        verify(ledgerOperationRepository, times(1)).findById(operationId);
        verify(ledgerEntryRepository, times(1)).findByOperationId(operationId);
        verifyNoMoreInteractions(ledgerOperationRepository);
        verifyNoMoreInteractions(ledgerEntryRepository);
    }

    @Test
    void getOperationEntries_OperationIdNull(){
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,() ->
                ledgerEntryQueryService.getOperationEntries(null)
        );
        assertEquals("operationId no puede ser null", ex.getMessage());
        verifyNoInteractions(ledgerEntryRepository);
        verifyNoInteractions(ledgerOperationRepository);
    }
    @Test
    void getOperationEntries_operationNotFound(){
        UUID operationId = UUID.randomUUID();

        when(ledgerOperationRepository.findById(operationId))
                .thenReturn(Optional.empty());
        NotFoundException ex = assertThrows(NotFoundException.class,() ->
                ledgerEntryQueryService.getOperationEntries(operationId)
        );
        assertEquals("Operación no encontrada para el id: " + operationId, ex.getMessage());
        verify(ledgerOperationRepository, times(1)).findById(operationId);
        verifyNoInteractions(ledgerEntryRepository);

    }

    @Test
    void getOperationEntries_entriesMissing(){
        UUID operationId = UUID.randomUUID();

        LedgerOperation op = mock(LedgerOperation.class);
        when(op.getId()).thenReturn(operationId);
        when(ledgerOperationRepository.findById(operationId))
                .thenReturn(Optional.of(op));
        when(ledgerEntryRepository.findByOperationId(operationId))
                .thenReturn(List.of());
        IllegalStateException ex = assertThrows(IllegalStateException.class,() ->
                ledgerEntryQueryService.getOperationEntries(operationId)
        );
        assertEquals("Inconsistencia: no se encontraron asientos para operationId: " + operationId, ex.getMessage());
        verify(ledgerOperationRepository, times(1)).findById(operationId);
        verify(ledgerEntryRepository, times(1)).findByOperationId(operationId);

    }


    // Test del método 4 getAccountBalance
    @Test
    void getAccountBalance_happyPath_shouldReturnBalance() {
        // Arrange
        Long accountId = 10L;

        BigDecimal credit = new BigDecimal("1000.00");
        BigDecimal debit  = new BigDecimal("400.00");
        BigDecimal expectedBalance = new BigDecimal("600.00");

        LedgerEntry lastEntry = mock(LedgerEntry.class);
        when(lastEntry.getCurrency()).thenReturn(Currency.PEN);

        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.CREDIT))
                .thenReturn(credit);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.DEBIT))
                .thenReturn(debit);
        when(ledgerEntryRepository.findFirstByAccountIdOrderByCreatedAtDesc(accountId))
                .thenReturn(Optional.of(lastEntry));

        // Act
        AccountBalanceResponse result = ledgerEntryQueryService.getAccountBalance(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(Currency.PEN, result.getCurrency());
        assertEquals(expectedBalance, result.getBalance());

        verify(ledgerEntryRepository, times(1))
                .sumAmountByAccountIdAndEntryType(accountId, EntryType.CREDIT);
        verify(ledgerEntryRepository, times(1))
                .sumAmountByAccountIdAndEntryType(accountId, EntryType.DEBIT);
        verify(ledgerEntryRepository, times(1))
                .findFirstByAccountIdOrderByCreatedAtDesc(accountId);

        verifyNoInteractions(ledgerOperationRepository);
        verifyNoMoreInteractions(ledgerEntryRepository);
    }
    @Test
    void getAccountBalance_accountIdNull_shouldThrow() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                ledgerEntryQueryService.getAccountBalance(null)
        );

        assertEquals("El account debe ser valido", ex.getMessage());
        verifyNoInteractions(ledgerEntryRepository);
        verifyNoInteractions(ledgerOperationRepository);
    }

    @Test
    void getAccountBalance_noEntries_shouldThrow(){
        Long accountId = 20L;

        when(ledgerEntryRepository.findFirstByAccountIdOrderByCreatedAtDesc(accountId))
                .thenReturn(Optional.empty());
        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                ledgerEntryQueryService.getAccountBalance(accountId)
        );
        assertEquals("No se encontraron asientos para la cuenta: " + accountId, ex.getMessage());
        verify(ledgerEntryRepository, times(1))
                .findFirstByAccountIdOrderByCreatedAtDesc(accountId);
        verifyNoInteractions(ledgerOperationRepository);
    }
    @Test
    void getAccountBalance_nullTotals_shouldReturnZeroDefaults() {
        // Arrange
        Long accountId = 10L;

        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.DEBIT))
                .thenReturn(null);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.CREDIT))
                .thenReturn(null);

        LedgerEntry lastEntry = mock(LedgerEntry.class);
        when(lastEntry.getCurrency()).thenReturn(Currency.PEN);

        when(ledgerEntryRepository.findFirstByAccountIdOrderByCreatedAtDesc(accountId))
                .thenReturn(Optional.of(lastEntry));

        // Act
        AccountBalanceResponse result = ledgerEntryQueryService.getAccountBalance(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(Currency.PEN, result.getCurrency());
        assertEquals(BigDecimal.ZERO, result.getBalance());

        verify(ledgerEntryRepository, times(1))
                .sumAmountByAccountIdAndEntryType(accountId, EntryType.DEBIT);
        verify(ledgerEntryRepository, times(1))
                .sumAmountByAccountIdAndEntryType(accountId, EntryType.CREDIT);
        verify(ledgerEntryRepository, times(1))
                .findFirstByAccountIdOrderByCreatedAtDesc(accountId);

        verifyNoInteractions(ledgerOperationRepository);
        // opcional:
        // verifyNoMoreInteractions(ledgerEntryRepository);
    }
    // Test del método 5 getAccountBalanceUpToDate
    @Test
    void getAccountBalanceUpToDate_HappyPath(){
        // Arrange
        Long accountId = 10L;
        Instant upToDate = Instant.now();
        BigDecimal credit = new BigDecimal("1500.00");
        BigDecimal debit = new BigDecimal("500.00");
        BigDecimal expectedBalance = new BigDecimal("1000.00");

        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeUpToDate(accountId,EntryType.CREDIT,upToDate))
                .thenReturn(credit);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeUpToDate(accountId,EntryType.DEBIT,upToDate))
                .thenReturn(debit);

        LedgerEntry entry = mock(LedgerEntry.class);
        when(entry.getCurrency()).thenReturn(Currency.PEN);

        when(ledgerEntryRepository.findFirstByAccountIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(accountId,upToDate))
                .thenReturn(Optional.of(entry));

        //Act
        AccountBalanceResponse result = ledgerEntryQueryService.getAccountBalanceUpToDate(accountId,upToDate);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(Currency.PEN, result.getCurrency());
        assertEquals(expectedBalance, result.getBalance());

        verify(ledgerEntryRepository, times(1))
                .sumAmountByAccountIdAndEntryTypeUpToDate(accountId,EntryType.CREDIT,upToDate);

        verify(ledgerEntryRepository, times(1))
                .sumAmountByAccountIdAndEntryTypeUpToDate(accountId,EntryType.DEBIT,upToDate);
        verify(ledgerEntryRepository, times(1))
                .findFirstByAccountIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(accountId,upToDate);

    }
    @Test
    void getAccountBalanceUpToDate_noEntries(){
        Long accountId = 10L;
        Instant upToDate = Instant.now();
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeUpToDate(accountId,EntryType.CREDIT,upToDate))
                .thenReturn(null);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeUpToDate(accountId,EntryType.DEBIT,upToDate))
                .thenReturn(null);
        when(ledgerEntryRepository.findFirstByAccountIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(accountId,upToDate))
                .thenReturn(Optional.empty());
        AccountBalanceResponse result = ledgerEntryQueryService.getAccountBalanceUpToDate(accountId,upToDate);
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertNull(result.getCurrency());
        assertEquals(BigDecimal.ZERO, result.getBalance());
        verify(ledgerEntryRepository, times(1))
                .sumAmountByAccountIdAndEntryTypeUpToDate(accountId,EntryType.CREDIT,upToDate);
        verify(ledgerEntryRepository, times(1))
                .sumAmountByAccountIdAndEntryTypeUpToDate(accountId,EntryType.DEBIT,upToDate);
        verify(ledgerEntryRepository, times(1))
                .findFirstByAccountIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(accountId,upToDate);
        verifyNoInteractions(ledgerOperationRepository);

    }
    @Test
    void getAccountBalanceUpToDate_accountIdNull(){
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,() ->
                ledgerEntryQueryService.getAccountBalanceUpToDate(null, Instant.now())
        );
        assertEquals("accountId debe ser válido", ex.getMessage());
        verifyNoInteractions(ledgerEntryRepository);
        verifyNoInteractions(ledgerOperationRepository);
    }
    @Test
    void getAccountBalanceUpToDate_upToDateNull(){
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,() ->
                ledgerEntryQueryService.getAccountBalanceUpToDate(10L, null)
        );
        assertEquals("upToDate no puede ser null", ex.getMessage());
        verifyNoInteractions(ledgerEntryRepository);
        verifyNoInteractions(ledgerOperationRepository);
    }


    // Test del método 6 listEntriesByOperationId
    @Test
    void getOperationEntriesByKey_happyPath() {
        String idempotencyKey = "idem-2026-02-01-0001";

        LedgerOperation op = mock(LedgerOperation.class);
        UUID opId = UUID.randomUUID();
        when(op.getId()).thenReturn(opId);

        LedgerEntry e1 = mock(LedgerEntry.class);
        LedgerEntry e2 = mock(LedgerEntry.class);
        List<LedgerEntry> entries = List.of(e1, e2);

        when(ledgerOperationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(op));
        when(ledgerEntryRepository.findByOperationId(opId))
                .thenReturn(entries);

        OperationEntries result =
                ledgerEntryQueryService.getOperationEntriesByIdempotencyKey(idempotencyKey);

        assertNotNull(result);
        assertSame(op, result.operation());
        assertSame(entries, result.entries());

        verify(ledgerOperationRepository, times(1)).findByIdempotencyKey(idempotencyKey);
        verify(ledgerEntryRepository, times(1)).findByOperationId(opId);

        verifyNoMoreInteractions(ledgerOperationRepository, ledgerEntryRepository);

    }

    @Test
    void
    getOperatioEntriesByKey_KeyNuLL(){
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,() ->
                ledgerEntryQueryService.getOperationEntriesByIdempotencyKey(null));
        assertEquals("idempotencyKey no puede ser nulo o vacío", ex.getMessage());
        verifyNoInteractions(ledgerEntryRepository);
        verifyNoInteractions(ledgerOperationRepository);
    }
    @Test
    void getOperationEntriesByKey_KeyBlank(){
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,() ->
                ledgerEntryQueryService.getOperationEntriesByIdempotencyKey("   "));
        assertEquals("idempotencyKey no puede ser nulo o vacío", ex.getMessage());
        verifyNoInteractions(ledgerEntryRepository);
        verifyNoInteractions(ledgerOperationRepository);
    }
    @Test
    void getOperationEntriesByKey_opNotFound(){
        String idempotencyKey = "idem-2026-02-01-0001";
        when(ledgerOperationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        NotFoundException ex = assertThrows(NotFoundException.class,() ->
                ledgerEntryQueryService.getOperationEntriesByIdempotencyKey(idempotencyKey)
        );
        assertEquals("Operación no encontrada para la idempotencyKey: " + idempotencyKey, ex.getMessage());
        verify(ledgerOperationRepository, times(1)).findByIdempotencyKey(idempotencyKey);
        verifyNoInteractions(ledgerEntryRepository);
        verifyNoMoreInteractions(ledgerOperationRepository);
    }
    @Test
    void getOperationEntriesByKey_noEntries() {
        String idempotencyKey = "idem-2026-02-01-0001";

        LedgerOperation op = mock(LedgerOperation.class);
        UUID opId = UUID.randomUUID();
        when(op.getId()).thenReturn(opId);

        when(ledgerOperationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(op));
        when(ledgerEntryRepository.findByOperationId(opId))
                .thenReturn(List.of());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ledgerEntryQueryService.getOperationEntriesByIdempotencyKey(idempotencyKey)
        );

        assertEquals("Inconsistencia: Operación " + opId + " no tiene entradas asociadas", ex.getMessage());

        verify(ledgerOperationRepository, times(1)).findByIdempotencyKey(idempotencyKey);
        verify(ledgerEntryRepository, times(1)).findByOperationId(opId);
    }






}
