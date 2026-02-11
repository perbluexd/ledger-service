package com.banca.ledger.web;

import com.banca.ledger.api.controller.AccountBalanceController;
import com.banca.ledger.api.dto.AccountBalanceResponse;
import com.banca.ledger.api.exception.GlobalExceptionHandler;
import com.banca.ledger.application.exception.NotFoundException;
import com.banca.ledger.application.service.LedgerEntryQueryService;
import com.banca.ledger.domain.enums.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AccountBalanceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AccountBalanceControllerWebMvcTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean LedgerEntryQueryService queryService;

    @Test
    void getAccountBalance_whenAccountExists_returns200() throws Exception {

        Long accountId = 10L;
        BigDecimal credit = new BigDecimal("100");
        BigDecimal debit = new BigDecimal("50");
        BigDecimal balance = credit.subtract(debit);

        AccountBalanceResponse response =
                new AccountBalanceResponse(accountId, Currency.PEN, balance);

        when(queryService.getAccountBalance(accountId))
                .thenReturn(response);

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accountId").value(10))
                .andExpect(jsonPath("$.currency").value("PEN"))
                .andExpect(jsonPath("$.balance").value(50));

        verify(queryService).getAccountBalance(accountId);
        verifyNoMoreInteractions(queryService);
    }

    @Test
    void getAccountBalance_whenAccountNotFound_returns404() throws Exception {

        Long accountId = 999L;

        when(queryService.getAccountBalance(accountId))
                .thenThrow(new NotFoundException("Account not found"));

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Account not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/accounts/999/balance"));

        verify(queryService).getAccountBalance(accountId);
        verifyNoMoreInteractions(queryService);
    }

    @Test
    void getAccountBalance_whenAccountIdIsNotParsable_returns400() throws Exception {

        String invalidAccountId = "abc";

        mockMvc.perform(get("/accounts/{accountId}/balance", invalidAccountId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);
    }

    @Test
    void getAccountBalanceUpToDate_whenRequestIsValid_returns200() throws Exception {

        Long accountId = 10L;
        BigDecimal credit = new BigDecimal("100");
        BigDecimal debit = new BigDecimal("50");
        BigDecimal balance = credit.subtract(debit);

        String upToDateRaw = "2026-01-01T00:00:00Z";
        Instant upToDate = Instant.parse(upToDateRaw);

        AccountBalanceResponse response =
                new AccountBalanceResponse(accountId, Currency.PEN, balance);

        when(queryService.getAccountBalanceUpToDate(accountId, upToDate))
                .thenReturn(response);

        mockMvc.perform(get("/accounts/{accountId}/balance/history", accountId)
                        .queryParam("upToDate", upToDateRaw)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accountId").value(10))
                .andExpect(jsonPath("$.currency").value("PEN"))
                .andExpect(jsonPath("$.balance").value(50));

        verify(queryService).getAccountBalanceUpToDate(accountId, upToDate);
        verifyNoMoreInteractions(queryService);
    }

    @Test
    void getAccountBalanceUpToDate_whenUpToDateCannotBeParsed_returns400() throws Exception {

        mockMvc.perform(get("/accounts/{accountId}/balance/history", 10L)
                        .queryParam("upToDate", "invalid-date")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);
    }

    @Test
    void getAccountBalanceUpToDate_whenUpToDateIsMissing_returns400() throws Exception {

        mockMvc.perform(get("/accounts/{accountId}/balance/history", 10L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);
    }

    @Test
    void getAccountBalanceUpToDate_whenAccountIdIsNonPositive_returns400() throws Exception {

        Long invalidAccountId = 0L;
        String upToDateRaw = "2026-01-01T00:00:00Z";
        Instant upToDate = Instant.parse(upToDateRaw);

        when(queryService.getAccountBalanceUpToDate(invalidAccountId, upToDate))
                .thenThrow(new IllegalArgumentException("accountId debe ser válido"));

        mockMvc.perform(get("/accounts/{accountId}/balance/history", invalidAccountId)
                        .queryParam("upToDate", upToDateRaw)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);

    }
}
