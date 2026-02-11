package com.banca.ledger.api.mapper;

import com.banca.ledger.api.dto.LedgerEntryResponse;
import com.banca.ledger.domain.model.LedgerEntry;
import org.springframework.stereotype.Component;

@Component
public class LedgerEntryMapper {

    public LedgerEntryResponse toResponse(LedgerEntry entry) {
        if (entry == null) return null;

        var op = entry.getOperation();

        LedgerEntryResponse res = new LedgerEntryResponse();
        res.setOperationId(op.getId());
        res.setId(entry.getId());
        res.setAccountId(entry.getAccountId());
        res.setEntryType(entry.getEntryType());
        res.setAmount(entry.getAmount());
        res.setCurrency(entry.getCurrency());
        res.setCreatedAt(entry.getCreatedAt());

        // reference vive en operation (nuevo modelo)
        if (op != null) {
            res.setReferenceType(op.getReferenceType());
            // si tu response incluye operationId:
            // res.setOperationId(op.getId());
        }

        return res;
    }
}
