package com.banca.ledger.application.service;

import com.banca.ledger.api.dto.AccountBalanceResponse;
import com.banca.ledger.api.dto.OperationDetailResponse;
import com.banca.ledger.application.exception.NotFoundException;
import com.banca.ledger.domain.enums.EntryType;
import com.banca.ledger.domain.model.LedgerEntry;
import com.banca.ledger.domain.model.LedgerOperation;
import com.banca.ledger.infrastructure.persistence.LedgerEntryRepository;
import com.banca.ledger.infrastructure.persistence.LedgerOperationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerEntryQueryService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerOperationRepository ledgerOperationRepository;

    public LedgerEntryQueryService(
            LedgerEntryRepository ledgerEntryRepository,
            LedgerOperationRepository ledgerOperationRepository
    ) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerOperationRepository = ledgerOperationRepository;
    }

    // UC-3: Listar movimientos por cuenta (paginado)
    @Transactional(readOnly = true)
    public Page<LedgerEntry> listEntries(Long accountId, int page, int size) {
        if (accountId == null || accountId <= 0) throw new IllegalArgumentException("accountId inválido");
        if (page < 0) throw new IllegalArgumentException("page no puede ser negativo");
        if (size <= 0 || size > 100) throw new IllegalArgumentException("size debe estar entre 1 y 100");

        Pageable pageable = PageRequest.of(page, size);
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    // UC-4B: Obtener detalle por entryId
    @Transactional(readOnly = true)
    public LedgerEntry getEntryDetail(Long entryId) {
        if (entryId == null || entryId <= 0) throw new IllegalArgumentException("entryId inválido");

        return ledgerEntryRepository.findDetailById(entryId)
                .orElseThrow(() -> new NotFoundException(
                        "Asiento contable no encontrado para el id: " + entryId
                ));
    }


    // UC-4A: Obtener detalle por operationId (DEVUELVE DOMINIO AGRUPADO)
    @Transactional(readOnly = true)
    public OperationEntries getOperationEntries(UUID operationId) {
        if (operationId == null) throw new IllegalArgumentException("operationId no puede ser null");

        LedgerOperation op = ledgerOperationRepository.findById(operationId)
                .orElseThrow(() -> new NotFoundException(
                        "Operación no encontrada para el id: " + operationId
                ));

        List<LedgerEntry> entries = ledgerEntryRepository.findByOperationId(op.getId());

        if (entries == null || entries.isEmpty()) {
            throw new IllegalStateException(
                    "Inconsistencia: no se encontraron asientos para operationId: " + operationId
            );
        }

        return new OperationEntries(op, entries);
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse getAccountBalance(Long accountId){
        if(accountId ==null || accountId <=0) throw new IllegalArgumentException("El account debe ser valido");
        BigDecimal totalCredits = ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.CREDIT);
        BigDecimal totalDebits = ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.DEBIT);

        BigDecimal balance = (totalCredits == null ? BigDecimal.ZERO : totalCredits)
                .subtract(totalDebits == null ? BigDecimal.ZERO : totalDebits);
        LedgerEntry entry = ledgerEntryRepository.findFirstByAccountIdOrderByCreatedAtDesc(accountId).orElseThrow(()
                -> new NotFoundException("No se encontraron asientos para la cuenta: " + accountId)
        );
        return new AccountBalanceResponse(accountId, entry.getCurrency(),balance);

    }
    @Transactional(readOnly = true)
    public AccountBalanceResponse getAccountBalanceUpToDate(Long accountId, Instant upToDate) {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("accountId debe ser válido");
        }
        if (upToDate == null) {
            throw new IllegalArgumentException("upToDate no puede ser null");
        }

        BigDecimal totalCredits =
                ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeUpToDate(
                        accountId, EntryType.CREDIT, upToDate
                );

        BigDecimal totalDebits =
                ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeUpToDate(
                        accountId, EntryType.DEBIT, upToDate
                );

        BigDecimal balance = (totalCredits == null ? BigDecimal.ZERO : totalCredits)
                .subtract(totalDebits == null ? BigDecimal.ZERO : totalDebits);

        // Si existe al menos una entry hasta upToDate → tomamos su currency
        // Si no existe ninguna → balance 0 y currency null
        return ledgerEntryRepository
                .findFirstByAccountIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(accountId, upToDate)
                .map(entry -> new AccountBalanceResponse(accountId, entry.getCurrency(), balance))
                .orElseGet(() -> new AccountBalanceResponse(accountId, null, BigDecimal.ZERO));
    }


    public OperationEntries getOperationEntriesByIdempotencyKey(String idempotencyKey) {
        if(idempotencyKey == null || idempotencyKey.isBlank()){
            throw new IllegalArgumentException("idempotencyKey no puede ser nulo o vacío");
        }
        LedgerOperation operation = ledgerOperationRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new NotFoundException(
                        "Operación no encontrada para la idempotencyKey: " + idempotencyKey
                ));
        List<LedgerEntry> entries = ledgerEntryRepository.findByOperationId(operation.getId());
        if(entries.isEmpty()){
            throw new IllegalStateException("Inconsistencia: Operación " + operation.getId() +
                    " no tiene entradas asociadas");
        }
        return new OperationEntries(operation,entries);


    }

    //

}
