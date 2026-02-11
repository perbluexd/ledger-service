package com.banca.ledger.infrastructure.persistence;

import com.banca.ledger.domain.enums.ReferenceType;
import com.banca.ledger.domain.model.LedgerEntry;
import com.banca.ledger.domain.model.LedgerOperation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerOperationRepository extends JpaRepository<LedgerOperation, UUID> {
    Optional<LedgerOperation> findByIdempotencyKey(String idempotencyKey);
    boolean existsByIdempotencyKey(String idempotencyKey);
    Optional<LedgerOperation> findByReferenceTypeAndReferenceId(ReferenceType referenceType,String referenceId);


}
