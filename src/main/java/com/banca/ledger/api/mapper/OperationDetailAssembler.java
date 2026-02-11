package com.banca.ledger.api.mapper;

import com.banca.ledger.api.dto.OperationDetailResponse;
import com.banca.ledger.domain.model.LedgerEntry;
import com.banca.ledger.domain.model.LedgerOperation;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class OperationDetailAssembler {

    private final LedgerEntryMapper ledgerEntryMapper;

    public OperationDetailAssembler(LedgerEntryMapper ledgerEntryMapper) {
        this.ledgerEntryMapper = ledgerEntryMapper;
    }

    public OperationDetailResponse toResponse(LedgerOperation operation, List<LedgerEntry> entries) {
        if (operation == null) {
            throw new IllegalArgumentException("operation no puede ser null");
        }

        List<LedgerEntry> safeEntries = (entries == null) ? List.of() : entries;

        var entryResponses = safeEntries.stream()
                .map(ledgerEntryMapper::toResponse)
                .toList();

        OperationDetailResponse response = new OperationDetailResponse();
        response.setOperationId(operation.getId());
        response.setCreatedAt(operation.getCreatedAt());
        response.setReferenceId(operation.getReferenceId());
        response.setReferenceType(operation.getReferenceType());
        response.setEntries(entryResponses);
        return response;
    }
}
