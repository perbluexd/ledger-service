package com.banca.ledger.api.dto;

import com.banca.ledger.domain.enums.Currency;
import lombok.*;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
@ToString
public class AccountBalanceResponse {
    private Long accountId;
    private Currency currency;
    private BigDecimal balance;
}
