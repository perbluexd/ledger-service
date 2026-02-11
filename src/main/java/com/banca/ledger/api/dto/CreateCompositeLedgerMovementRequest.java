package com.banca.ledger.api.dto;

import com.banca.ledger.domain.enums.Currency;
import com.banca.ledger.domain.enums.ReferenceType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
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
public class CreateCompositeLedgerMovementRequest {

    @NotNull
    @Positive(message = "debitAccountId debe ser válido")
    private Long debitAccountId;

    @NotNull
    @Positive(message = "creditAccountId debe ser válido")
    private Long creditAccountId;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true, message = "amount debe ser mayor a 0")
    private BigDecimal amount;

    @NotNull
    private Currency currency;

    @NotNull
    private ReferenceType referenceType;

    @NotBlank(message = "referenceId no puede ser vacío")
    private String referenceId;

    @NotBlank(message = "idempotencyKey no puede ser vacío")
    private String idempotencyKey;

    @AssertTrue(message = "debitAccountId y creditAccountId deben ser diferentes")
    public boolean isAccountsDifferent() {
        return debitAccountId != null
                && creditAccountId != null
                && !debitAccountId.equals(creditAccountId);
    }
}
