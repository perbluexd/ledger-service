package com.banca.ledger.domain.model;

import com.banca.ledger.domain.enums.Currency;
import com.banca.ledger.domain.enums.EntryType;
import com.banca.ledger.domain.enums.ReferenceType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "ledger_entries")
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED) // requerido por JPA
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 50)
    private EntryType entryType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Currency currency;



    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) // optional puede ser true si UC-1 lo dejas sin operación
    @JoinColumn(name = "operation_id", nullable = false)
    private LedgerOperation operation;


    @Builder
    public LedgerEntry(
            Long accountId,
            EntryType entryType,
            BigDecimal amount,
            Currency currency,
            LedgerOperation operation
    ) {
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
        this.currency = currency;
        this.operation = operation;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * equals/hashCode seguro para entidades JPA:
     * - Dos entidades son iguales SOLO si ambas tienen id no nulo y el id coincide.
     * - Evita problemas cuando id es null (pre-persist).
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;

        // importante en Hibernate: puede devolverte proxys
        if (getClass() != o.getClass()) return false;

        LedgerEntry that = (LedgerEntry) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public final int hashCode() {
        // Patrón recomendado para evitar que el hash cambie al asignar id después:
        // constante basada en clase.
        return getClass().hashCode();
    }
}
