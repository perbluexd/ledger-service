package com.banca.ledger.api.dto;

import com.banca.ledger.domain.enums.ReferenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OperationDetailResponse {
    @NotBlank
    @NotNull
    private UUID operationId;
    @NotNull
    private ReferenceType referenceType;
    @NotNull
    private String referenceId;
    @NotNull
    private Instant createdAt;
    @NotNull
    List<LedgerEntryResponse> entries;

}
