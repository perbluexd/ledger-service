package com.banca.ledger.domain.model;

import com.banca.ledger.domain.enums.ReferenceType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ledger_operations")
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class LedgerOperation {
    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 512, name = "idempotency_key")
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false)
    private ReferenceType referenceType;

    @Column(name = "reference_id", nullable = false, length = 100)
    private String referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;





    public LedgerOperation(String idempotencyKey, ReferenceType referenceType, String referenceId) {
        this.idempotencyKey = idempotencyKey;
        this.referenceType = referenceType;
        this.referenceId = referenceId;

    }

    @PrePersist
    void prePersiste(){
        if(createdAt == null){
            createdAt = Instant.now();
        }
        if(id == null) {id = UUID.randomUUID();}
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (getClass() != o.getClass()) return false;
        LedgerOperation that = (LedgerOperation) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();

    }


}
