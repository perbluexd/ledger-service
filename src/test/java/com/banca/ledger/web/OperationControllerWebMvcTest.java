package com.banca.ledger.web;

import com.banca.ledger.api.controller.OperationController;
import com.banca.ledger.api.dto.LedgerEntryResponse;
import com.banca.ledger.api.dto.OperationDetailResponse;
import com.banca.ledger.api.exception.GlobalExceptionHandler;
import com.banca.ledger.api.mapper.OperationDetailAssembler;
import com.banca.ledger.application.exception.NotFoundException;
import com.banca.ledger.application.service.LedgerEntryCommandService;
import com.banca.ledger.application.service.LedgerEntryQueryService;
import com.banca.ledger.application.service.OperationEntries;
import com.banca.ledger.domain.enums.Currency;
import com.banca.ledger.domain.enums.EntryType;
import com.banca.ledger.domain.enums.ReferenceType;
import com.banca.ledger.domain.model.LedgerEntry;
import com.banca.ledger.domain.model.LedgerOperation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OperationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class OperationControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean LedgerEntryCommandService commandService;
    @MockitoBean OperationDetailAssembler assembler;
    @MockitoBean LedgerEntryQueryService queryService;

    @Test
    void getByIdempotencyKey_whenOperationIsFound_returns200() throws Exception {

        String idempotencyKey = "idem-x";

        // Puedes usar mocks para dominio (no necesitas builders reales ni ids reales en dominio)
        LedgerOperation op = mock(LedgerOperation.class);
        LedgerEntry e1 = mock(LedgerEntry.class);
        LedgerEntry e2 = mock(LedgerEntry.class);

        List<LedgerEntry> entries = List.of(e1, e2);
        OperationEntries result = new OperationEntries(op, entries);

        UUID opId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

        Long accountId = 10L;

        LedgerEntryResponse r1 = new LedgerEntryResponse(
                1L, opId, accountId, EntryType.CREDIT, new BigDecimal("100.00"),
                Currency.PEN, ReferenceType.DEPOSIT, Instant.parse("2026-01-01T00:00:00Z")
        );

        LedgerEntryResponse r2 = new LedgerEntryResponse(
                2L, opId, accountId, EntryType.CREDIT, new BigDecimal("50.00"),
                Currency.PEN, ReferenceType.DEPOSIT, Instant.parse("2026-01-02T00:00:00Z")
        );

        OperationDetailResponse response = new OperationDetailResponse(
                opId,
                ReferenceType.DEPOSIT,
                "DEP-001",
                createdAt,
                List.of(r1, r2)
        );

        when(queryService.getOperationEntriesByIdempotencyKey(idempotencyKey))
                .thenReturn(result);

        // IMPORTANTE: tu controller llama assembler.toResponse(op, entries)
        when(assembler.toResponse(op, entries))
                .thenReturn(response);

        mockMvc.perform(get("/operations/{idempotencyKey}", idempotencyKey)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))

                // Campos del OperationDetailResponse
                .andExpect(jsonPath("$.operationId").value(opId.toString()))
                .andExpect(jsonPath("$.referenceType").value("DEPOSIT"))
                .andExpect(jsonPath("$.referenceId").value("DEP-001"))
                .andExpect(jsonPath("$.createdAt").value(createdAt.toString()))

                // Entries array
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries.length()").value(2))

                // Entry 1 (LedgerEntryResponse: id, operationId, accountId, entryType, amount, currency, referenceType, createdAt)
                .andExpect(jsonPath("$.entries[0].id").value(1))
                .andExpect(jsonPath("$.entries[0].operationId").value(opId.toString()))
                .andExpect(jsonPath("$.entries[0].accountId").value(10))
                .andExpect(jsonPath("$.entries[0].entryType").value("CREDIT"))
                .andExpect(jsonPath("$.entries[0].amount").value(100.00))
                .andExpect(jsonPath("$.entries[0].currency").value("PEN"))
                .andExpect(jsonPath("$.entries[0].referenceType").value("DEPOSIT"))
                .andExpect(jsonPath("$.entries[0].createdAt").value("2026-01-01T00:00:00Z"))

                // Entry 2
                .andExpect(jsonPath("$.entries[1].id").value(2))
                .andExpect(jsonPath("$.entries[1].operationId").value(opId.toString()))
                .andExpect(jsonPath("$.entries[1].accountId").value(10))
                .andExpect(jsonPath("$.entries[1].entryType").value("CREDIT"))
                .andExpect(jsonPath("$.entries[1].amount").value(50.00))
                .andExpect(jsonPath("$.entries[1].currency").value("PEN"))
                .andExpect(jsonPath("$.entries[1].referenceType").value("DEPOSIT"))
                .andExpect(jsonPath("$.entries[1].createdAt").value("2026-01-02T00:00:00Z"));

        verify(queryService).getOperationEntriesByIdempotencyKey(idempotencyKey);
        verify(assembler).toResponse(op, entries);

        verifyNoMoreInteractions(queryService, assembler);
        verifyNoInteractions(commandService);
    }

    @Test
    void getByIdempotencyKey_whenOperationNotFound_returns404() throws Exception{
        String idempotencyKey = "idem-x";

        when(queryService.getOperationEntriesByIdempotencyKey(idempotencyKey))
                .thenThrow(new NotFoundException("No se encontró operación con idempotencyKey: " + idempotencyKey));
        mockMvc.perform(get("/operations/{idempotencyKey}", idempotencyKey)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        verifyNoInteractions(assembler);
        verify(queryService).getOperationEntriesByIdempotencyKey(idempotencyKey);
         verifyNoMoreInteractions(queryService);
         verifyNoInteractions(commandService);
    }
    @Test
    void getByIdempotencyKey_whenIdempotencyKeyIsBlank_returns400() throws Exception {
        String idempotencyKey = " "; // blank realista (se enviará como %20)



        mockMvc.perform(get("/operations/{idempotencyKey}", idempotencyKey)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);
        verifyNoInteractions(assembler);
        verifyNoInteractions(commandService);

    }

    // test del endpoint de operationController de inversión

    @Test
    void reverseOperation_whenOperationExists_returns200() throws Exception {

        UUID operationId = UUID.randomUUID();

        // Dominio simulado (no necesitas lógica real aquí)
        LedgerOperation reversedOp = mock(LedgerOperation.class);

        LedgerEntry e1Reversed = mock(LedgerEntry.class);
        LedgerEntry e2Reversed = mock(LedgerEntry.class);

        List<LedgerEntry> reversedEntries = List.of(e1Reversed, e2Reversed);

        OperationEntries result = new OperationEntries(reversedOp, reversedEntries);

        Instant createdAt = Instant.parse("2026-01-03T00:00:00Z");

        OperationDetailResponse response = new OperationDetailResponse(
                operationId,
                ReferenceType.DEPOSIT,
                "DEP-001",
                createdAt,
                List.of(
                        new LedgerEntryResponse(
                                3L, operationId, 10L, EntryType.DEBIT,
                                new BigDecimal("100.00"),
                                Currency.PEN, ReferenceType.DEPOSIT,
                                createdAt
                        ),
                        new LedgerEntryResponse(
                                4L, operationId, 10L, EntryType.DEBIT,
                                new BigDecimal("50.00"),
                                Currency.PEN, ReferenceType.DEPOSIT,
                                createdAt
                        )
                )
        );

        when(commandService.reverseOperation(operationId))
                .thenReturn(result);

        when(assembler.toResponse(reversedOp, reversedEntries))
                .thenReturn(response);

        mockMvc.perform(post("/operations/{operationId}", operationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))

                .andExpect(jsonPath("$.operationId").value(operationId.toString()))
                .andExpect(jsonPath("$.referenceType").value("DEPOSIT"))
                .andExpect(jsonPath("$.referenceId").value("DEP-001"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-03T00:00:00Z"))

                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries.length()").value(2))

                .andExpect(jsonPath("$.entries[0].id").value(3))
                .andExpect(jsonPath("$.entries[0].accountId").value(10))
                .andExpect(jsonPath("$.entries[0].entryType").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].amount").value(100.00))

                .andExpect(jsonPath("$.entries[1].id").value(4))
                .andExpect(jsonPath("$.entries[1].accountId").value(10))
                .andExpect(jsonPath("$.entries[1].entryType").value("DEBIT"))
                .andExpect(jsonPath("$.entries[1].amount").value(50.00));

        verify(commandService).reverseOperation(operationId);
        verify(assembler).toResponse(reversedOp, reversedEntries);

        verifyNoMoreInteractions(commandService, assembler);
        verifyNoInteractions(queryService);
    }


    @Test
    void reverseOperation_whenOperationNotFound_returns404() throws Exception{
        UUID operationId = UUID.randomUUID();
        when(commandService.reverseOperation(operationId))
                .thenThrow(new NotFoundException("No se encontró operación con id: " + operationId));
        mockMvc.perform(post("/operations/{operationId}",operationId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(commandService).reverseOperation(operationId);
        verifyNoMoreInteractions(commandService);
        verifyNoInteractions(queryService, assembler);

    }

    @Test
    void reverseOperation_whenOperationIdIsNotValidUuid_returns400() throws Exception {

        String invalidOperationId = "not-a-uuid";

        mockMvc.perform(post("/operations/{operationId}", invalidOperationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commandService);
        verifyNoInteractions(assembler);
        verifyNoInteractions(queryService);
    }





}
