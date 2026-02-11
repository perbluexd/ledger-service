package com.banca.ledger.api.controller;

import com.banca.ledger.api.dto.AccountBalanceResponse;
import com.banca.ledger.application.service.LedgerEntryQueryService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/accounts")
@Validated
public class AccountBalanceController {

    private final LedgerEntryQueryService queryService;

    public AccountBalanceController(LedgerEntryQueryService queryService) {
        this.queryService = queryService;
    }

    // UC-5: saldo actual
    @GetMapping("/{accountId}/balance")
    public ResponseEntity<AccountBalanceResponse> getAccountBalance(
            @PathVariable
            @NotNull(message = "accountId es requerido")
            @Positive(message = "accountId debe ser válido")
            Long accountId
    ) {
        return ResponseEntity.ok(queryService.getAccountBalance(accountId));
    }

    // UC-6: saldo histórico (hasta una fecha)
    // Ejemplo: /accounts/10/balance/history?upToDate=2026-01-01T00:00:00Z
    @GetMapping("/{accountId}/balance/history")
    public ResponseEntity<AccountBalanceResponse> getAccountBalanceUpToDate(
            @PathVariable
            @NotNull(message = "accountId es requerido")
            @Positive(message = "accountId debe ser válido")
            Long accountId,

            @RequestParam(name = "upToDate")
            @NotNull(message = "upToDate es requerido")
            Instant upToDate
    ) {
        return ResponseEntity.ok(queryService.getAccountBalanceUpToDate(accountId, upToDate));
    }
}
