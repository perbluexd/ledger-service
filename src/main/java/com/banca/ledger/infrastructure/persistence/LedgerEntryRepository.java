package com.banca.ledger.infrastructure.persistence;

import com.banca.ledger.domain.enums.EntryType;
import com.banca.ledger.domain.model.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry,Long> {

    Page<LedgerEntry> findByAccountId(Long accountId, Pageable pageable);

    Page<LedgerEntry> findByAccountIdAndCreatedAtBetween(Long accountId, Instant from, Instant to, Pageable pageable);

    /*
    @EntityGraph permite definir, por método de repositorio, qué asociaciones LAZY deben cargarse junto con la entidad principal, evitando
     LazyInitializationException sin cambiar el fetch global.
     */
    @EntityGraph(attributePaths = "operation")
    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    @EntityGraph(attributePaths = "operation")
    List<LedgerEntry> findByOperationId(UUID operationId);

    Optional<LedgerEntry> findFirstByOperationId(UUID operationId);
    @Query("""
    SELECT COALESCE(SUM(le.amount), 0)
    FROM LedgerEntry le
    WHERE le.accountId = :accountId
      AND le.entryType = :entryType
""")
    BigDecimal sumAmountByAccountIdAndEntryType(
            @Param("accountId") Long accountId,
            @Param("entryType") EntryType entryType
    );

    Optional<LedgerEntry> findFirstByAccountIdOrderByCreatedAtDesc(Long accountId);

    @Query("""
    SELECT COALESCE(SUM(le.amount), 0)
    FROM LedgerEntry le
    WHERE le.accountId = :accountId
      AND le.entryType = :entryType
      AND le.createdAt <= :upToDate
""")
    BigDecimal sumAmountByAccountIdAndEntryTypeUpToDate(
            @Param("accountId") Long accountId,
            @Param("entryType") EntryType entryType,
            @Param("upToDate") Instant upToDate
    );

    Optional<LedgerEntry> findFirstByAccountIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(Long accountId, Instant upToDate);


    @EntityGraph(attributePaths = "operation")
    @Query("select le from LedgerEntry le where le.id = :entryId")
    Optional<LedgerEntry> findDetailById(@Param("entryId") Long entryId);





}
