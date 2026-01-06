package com.banca.ledger.api.dto;

import com.banca.ledger.domain.enums.Currency;
import com.banca.ledger.domain.enums.EntryType;
import com.banca.ledger.domain.enums.ReferenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CreateLedgerEntryRequest {
    @NotNull
    private Long accountId;

    @NotNull
    private EntryType entryType;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotNull
    private Currency currency;

    @NotNull
    private ReferenceType referenceType;

    private String referenceId;

    @NotBlank
    private String idempotencyKey;



}
