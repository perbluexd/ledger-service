package com.banca.ledger.api.mapper;

import com.banca.ledger.api.dto.CreateCompositeLedgerMovementRequest;
import com.banca.ledger.domain.enums.EntryType;
import com.banca.ledger.domain.model.LedgerEntry;
import com.banca.ledger.domain.model.LedgerOperation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CompositeMovementAssembler {
    public List<LedgerEntry> toEntries(CreateCompositeLedgerMovementRequest request, LedgerOperation op){
        LedgerEntry entryDebit = new LedgerEntry(
                request.getDebitAccountId(),
                EntryType.DEBIT,
                request.getAmount(),
                request.getCurrency(),
                op
        );
        LedgerEntry debitCredit = new LedgerEntry(
                request.getCreditAccountId(),
                EntryType.CREDIT,
                request.getAmount(),
                request.getCurrency(),
                op
        );
        return List.of(entryDebit, debitCredit);
    }
}
