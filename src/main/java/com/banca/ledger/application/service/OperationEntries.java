package com.banca.ledger.application.service;

import com.banca.ledger.domain.model.LedgerEntry;
import com.banca.ledger.domain.model.LedgerOperation;

import java.util.List;

public record OperationEntries(LedgerOperation operation, List<LedgerEntry> entries) {}
