package com.banca.ledger.web;

import com.banca.ledger.api.controller.LedgerEntryController;
import com.banca.ledger.api.dto.CreateCompositeLedgerMovementRequest;
import com.banca.ledger.api.dto.CreateLedgerEntryRequest;
import com.banca.ledger.api.dto.LedgerEntryResponse;
import com.banca.ledger.api.dto.OperationDetailResponse;
import com.banca.ledger.api.exception.GlobalExceptionHandler;
import com.banca.ledger.api.mapper.LedgerEntryMapper;
import com.banca.ledger.api.mapper.OperationDetailAssembler;
import com.banca.ledger.application.exception.ConflictException;
import com.banca.ledger.application.exception.NotFoundException;
import com.banca.ledger.application.service.LedgerEntryCommandService;
import com.banca.ledger.application.service.LedgerEntryQueryService;
import com.banca.ledger.application.service.OperationEntries;
import com.banca.ledger.domain.enums.Currency;
import com.banca.ledger.domain.enums.EntryType;
import com.banca.ledger.domain.enums.ReferenceType;
import com.banca.ledger.domain.model.LedgerEntry;
import com.banca.ledger.domain.model.LedgerOperation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;



@WebMvcTest(controllers = LedgerEntryController.class)
@AutoConfigureMockMvc(addFilters = false) // evita que SecurityFilterChain interfiera por ahora
@Import(GlobalExceptionHandler.class)
class LedgerEntryControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ====== Dependencias del Controller (mockeadas) ======
    @MockitoBean LedgerEntryCommandService commandService;
    @MockitoBean LedgerEntryQueryService queryService;
    @MockitoBean LedgerEntryMapper ledgerEntryMapper;
    @MockitoBean OperationDetailAssembler operationDetailAssembler;

    @Test
    void createEntry_returns200_andResponseJson() throws Exception {
        // Arrange (request)
        CreateLedgerEntryRequest request = new CreateLedgerEntryRequest(
                10L,
                EntryType.CREDIT,
                new BigDecimal("150.25"),
                Currency.PEN,              // ajusta si tu enum se llama distinto
                ReferenceType.DEPOSIT,     // ajusta si tu enum se llama distinto
                "INV-001",
                "idem-123"
        );

        // Arrange (domain returned by service)
        UUID opId = UUID.randomUUID();
        LedgerOperation op = new LedgerOperation("idem-123", ReferenceType.DEPOSIT, "INV-001");
        // ojo: en domain el id se setea en @PrePersist, así que aquí no lo tendrás.
        // Para el test web no importa el id real del dominio; mapeamos a response manualmente.

        LedgerEntry domainSaved = LedgerEntry.builder()
                .accountId(10L)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("150.25"))
                .currency(Currency.PEN)
                .operation(op)
                .build();

        // Arrange (response)
        LedgerEntryResponse response = new LedgerEntryResponse(
                1L,
                opId,
                10L,
                EntryType.CREDIT,
                new BigDecimal("150.25"),
                Currency.PEN,
                ReferenceType.DEPOSIT,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        when(commandService.createEntry(any(CreateLedgerEntryRequest.class))).thenReturn(domainSaved);
        when(ledgerEntryMapper.toResponse(any(LedgerEntry.class))).thenReturn(response);

        // Act + Assert
        mockMvc.perform(post("/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.operationId").value(opId.toString()))
                .andExpect(jsonPath("$.accountId").value(10))
                .andExpect(jsonPath("$.entryType").value("CREDIT"))
                .andExpect(jsonPath("$.amount").value(150.25))
                .andExpect(jsonPath("$.currency").value("PEN"))
                .andExpect(jsonPath("$.referenceType").value("DEPOSIT"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"));
    }

    @Test
    void createEntry_whenMissingIdempotencyKey_returns400() throws Exception {
        // idempotencyKey tiene @NotBlank => debe dar 400 por Bean Validation
        CreateLedgerEntryRequest badRequest = new CreateLedgerEntryRequest(
                10L,
                EntryType.CREDIT,
                new BigDecimal("150.25"),
                Currency.PEN,
                ReferenceType.DEPOSIT,
                "INV-001",
                "" // inválido
        );

        mockMvc.perform(post("/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest))
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void createEntry_whenConflictException_returns409() throws Exception {
        CreateLedgerEntryRequest request = new CreateLedgerEntryRequest(
                10L,
                EntryType.CREDIT,
                new BigDecimal("150.25"),
                Currency.PEN,
                ReferenceType.DEPOSIT,
                "INV-001",
                "idem-123"
        );

        when(commandService.createEntry(any(CreateLedgerEntryRequest.class)))
                .thenThrow(new ConflictException("Conflicto de idempotencia"));

        mockMvc.perform(post("/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isConflict());
    }

    // Tests para el segundo endpoint de la clase ledgerEntryController  de nombre listentries

    @Test
    void listEntries_returns200_andPageResponse() throws Exception {
        // Arrange
        long accountId = 10L;
        int page = 0;
        int size = 20;

        UUID opId = UUID.randomUUID(); // solo para el response (JSON)
        LedgerOperation op = new LedgerOperation("idem-x", ReferenceType.DEPOSIT, "DEP-001");

        LedgerEntry e1 = LedgerEntry.builder()
                .accountId(accountId)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("100.00"))
                .currency(Currency.PEN)
                .operation(op)
                .build();

        LedgerEntry e2 = LedgerEntry.builder()
                .accountId(accountId)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("50.00"))
                .currency(Currency.PEN)
                .operation(op)
                .build();

        Pageable pageable = PageRequest.of(page, size);
        Page<LedgerEntry> servicePage = new PageImpl<>(
                List.of(e1, e2),
                pageable,
                2 // totalElements
        );

        LedgerEntryResponse r1 = new LedgerEntryResponse(
                1L, opId, accountId, EntryType.CREDIT, new BigDecimal("100.00"),
                Currency.PEN, ReferenceType.DEPOSIT, Instant.parse("2026-01-01T00:00:00Z")
        );

        LedgerEntryResponse r2 = new LedgerEntryResponse(
                2L, opId, accountId, EntryType.CREDIT, new BigDecimal("50.00"),
                Currency.PEN, ReferenceType.DEPOSIT, Instant.parse("2026-01-02T00:00:00Z")
        );

        when(queryService.listEntries(accountId, page, size)).thenReturn(servicePage);
        when(ledgerEntryMapper.toResponse(e1)).thenReturn(r1);
        when(ledgerEntryMapper.toResponse(e2)).thenReturn(r2);

        // Act + Assert
        mockMvc.perform(get("/entries/accounts/{accountId}", accountId)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // Page JSON: content array
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))

                // Verificamos 2 campos clave del primer elemento
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].accountId").value((int) accountId))
                .andExpect(jsonPath("$.content[0].amount").value(100.00))

                // y del segundo
                .andExpect(jsonPath("$.content[1].id").value(2))
                .andExpect(jsonPath("$.content[1].accountId").value((int) accountId))
                .andExpect(jsonPath("$.content[1].amount").value(50.00))

                // metadata típica de Page (puede variar, pero normalmente está)
                .andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.number").value(page))
                .andExpect(jsonPath("$.totalElements").value(2));
    }
    @Test
    void listEntries_whenSizeIsGreaterThan100_returns400() throws Exception {
        long accountId = 10L;
        int page = 0;
        int size = 200;



        mockMvc.perform(get("/entries/accounts/{accountId}", accountId)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);
        verifyNoInteractions(ledgerEntryMapper);


    }

    @Test
    void listEntries_whenAccountIdIsInvalid_returns400() throws Exception {
        long accountId = -1L;
        int page = 0;
        int size = 2;



        mockMvc.perform(get("/entries/accounts/{accountId}", accountId)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);
        verifyNoInteractions(ledgerEntryMapper);

    }


    @Test
    void listEntries_whenPageIsNegative_returns400() throws Exception{
        long accountId = 10L;
        int page = -1;
        int size = 2;
        mockMvc.perform(get("/entries/accounts/{accountId}", accountId)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .accept(MediaType.APPLICATION_JSON)
               )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);
        verifyNoInteractions(ledgerEntryMapper);
    }
    // tst del endpoint createCompositeMovement
    @Test
    void createCompositeMovement_returns200_andOperationDetailResponse() throws Exception {
        CreateCompositeLedgerMovementRequest request =
                new CreateCompositeLedgerMovementRequest(
                        10L, 20L, new BigDecimal("150.00"),
                        Currency.PEN, ReferenceType.DEPOSIT, "DEP-001", "idem-composite-001"
                );

        UUID operationId = UUID.randomUUID();

        LedgerOperation operation = new LedgerOperation("idem-composite-001", ReferenceType.DEPOSIT, "DEP-001");

        LedgerEntry debitEntry = LedgerEntry.builder()
                .accountId(10L).entryType(EntryType.DEBIT)
                .amount(new BigDecimal("150.00")).currency(Currency.PEN)
                .operation(operation).build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .accountId(20L).entryType(EntryType.CREDIT)
                .amount(new BigDecimal("150.00")).currency(Currency.PEN)
                .operation(operation).build();

        LedgerEntryResponse debitResponse =
                new LedgerEntryResponse(1L, operationId, 10L, EntryType.DEBIT, new BigDecimal("150.00"),
                        Currency.PEN, ReferenceType.DEPOSIT, Instant.parse("2026-01-01T10:00:00Z"));

        LedgerEntryResponse creditResponse =
                new LedgerEntryResponse(2L, operationId, 20L, EntryType.CREDIT, new BigDecimal("150.00"),
                        Currency.PEN, ReferenceType.DEPOSIT, Instant.parse("2026-01-01T10:00:01Z"));

        Instant date = Instant.parse("2026-01-01T10:00:00Z");

        OperationEntries serviceResult = new OperationEntries(operation, List.of(debitEntry, creditEntry));
        OperationDetailResponse resultado =
                new OperationDetailResponse(operationId, operation.getReferenceType(), operation.getReferenceId(),
                        date, List.of(debitResponse, creditResponse));

        when(commandService.recordCompositeMovement(any(CreateCompositeLedgerMovementRequest.class)))
                .thenReturn(serviceResult);

        when(operationDetailAssembler.toResponse(any(LedgerOperation.class), anyList()))
                .thenReturn(resultado);

        mockMvc.perform(post("/entries/composite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.operationId").value(operationId.toString()))
                .andExpect(jsonPath("$.referenceType").value("DEPOSIT"))
                .andExpect(jsonPath("$.referenceId").value("DEP-001"))
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].id").value(1))
                .andExpect(jsonPath("$.entries[0].accountId").value(10))
                .andExpect(jsonPath("$.entries[0].entryType").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].amount").value(150.00))
                .andExpect(jsonPath("$.entries[1].id").value(2))
                .andExpect(jsonPath("$.entries[1].accountId").value(20))
                .andExpect(jsonPath("$.entries[1].entryType").value("CREDIT"))
                .andExpect(jsonPath("$.entries[1].amount").value(150.00));
    }
    @Test
    void createCompositeMovement_whenDebitAndCreditAccountAreEqual_returns400() throws Exception{
        CreateCompositeLedgerMovementRequest request =
                new CreateCompositeLedgerMovementRequest(
                        10L, 10L, new BigDecimal("150.00"),
                        Currency.PEN, ReferenceType.DEPOSIT, "DEP-001", "idem-composite-001"
                );
        mockMvc.perform(post("/entries/composite")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(operationDetailAssembler);
    }
    @Test
    void createCompositeMovement_whenAmountIsZero_returns400() throws Exception {
        CreateCompositeLedgerMovementRequest request =
                new CreateCompositeLedgerMovementRequest(
                        10L, 20L, new BigDecimal("0.00"),
                        Currency.PEN, ReferenceType.DEPOSIT, "DEP-001", "idem-composite-001"
                );

        mockMvc.perform(post("/entries/composite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commandService);
        verifyNoInteractions(operationDetailAssembler);
    }

    @Test
    void createCompositeMovement_whenIdempotencyKeyIsBlank_returns400() throws Exception{
        CreateCompositeLedgerMovementRequest request =
                new CreateCompositeLedgerMovementRequest(
                        10L, 20L, new BigDecimal("-10.00"),
                        Currency.PEN, ReferenceType.DEPOSIT, "DEP-001", ""
                );
        mockMvc.perform(post("/entries/composite")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(commandService);
        verifyNoInteractions(operationDetailAssembler);
    }

    @Test
    void createCompositeMovement_whenReferenceIdIsBlank_returns400() throws Exception{
        CreateCompositeLedgerMovementRequest request =
                new CreateCompositeLedgerMovementRequest(
                        10L, 20L, new BigDecimal("-10.00"),
                        Currency.PEN, ReferenceType.DEPOSIT, "", "idem-composite-001"
                );
        mockMvc.perform(post("/entries/composite")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(commandService);
        verifyNoInteractions(operationDetailAssembler);
    }

    @Test
    void createCompositeMovement_whenIdempotencyConflict_returns409() throws Exception{
        CreateCompositeLedgerMovementRequest request =
                new CreateCompositeLedgerMovementRequest(
                        10L, 20L, new BigDecimal("150.00"),
                        Currency.PEN, ReferenceType.DEPOSIT, "DEP-001", "idem-composite-001"
                );
        when(commandService.recordCompositeMovement(any())).thenThrow(new ConflictException("Idempotency conflict"));
        mockMvc.perform(post("/entries/composite")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // test del endpoint getEntryDetail
    @Test
    void getEntryDetail_whenEntryExists_returns200_andLedgerEntryResponse() throws Exception {
        // Arrange (domain returned by service)
        UUID opId = UUID.randomUUID();
        LedgerOperation op = new LedgerOperation("idem-123", ReferenceType.DEPOSIT, "INV-001");

        LedgerEntry entry = LedgerEntry.builder()
                .accountId(10L)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("150.25"))
                .currency(Currency.PEN)
                .operation(op)
                .build();

        // Arrange (response DTO)
        LedgerEntryResponse response = new LedgerEntryResponse(
                1L,
                opId,
                10L,
                EntryType.CREDIT,
                new BigDecimal("150.25"),
                Currency.PEN,
                ReferenceType.DEPOSIT,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        when(queryService.getEntryDetail(1L)).thenReturn(entry);
        when(ledgerEntryMapper.toResponse(entry)).thenReturn(response);

        // Act + Assert
        mockMvc.perform(get("/entries/{entryId}", 1L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.operationId").value(opId.toString()))
                .andExpect(jsonPath("$.accountId").value(10))
                .andExpect(jsonPath("$.entryType").value("CREDIT"))
                .andExpect(jsonPath("$.amount").value(150.25))
                .andExpect(jsonPath("$.currency").value("PEN"))
                .andExpect(jsonPath("$.referenceType").value("DEPOSIT"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"));

        // Optional: verify flow
        verify(queryService).getEntryDetail(1L);
        verify(ledgerEntryMapper).toResponse(entry);
        verifyNoMoreInteractions(queryService, ledgerEntryMapper);
    }

    @Test
    void getEntryDetail_whenEntryIsNotFound_returns404() throws Exception {

        long entryId = 99L;

        when(queryService.getEntryDetail(entryId))
                .thenThrow(new NotFoundException("Entry not found"));

        mockMvc.perform(get("/entries/{entryId}", entryId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(queryService).getEntryDetail(entryId);
        verifyNoInteractions(ledgerEntryMapper);
    }


    @Test
    void getEntryDetail_whenEntryIdIsInvalid_returns400() throws Exception {

        long invalidEntryId = -5L;

        mockMvc.perform(get("/entries/{entryId}", invalidEntryId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);
        verifyNoInteractions(ledgerEntryMapper);
    }


    // Test del endpoint getOperationDetail
    @Test
    void getOperationDetail_whenOperationExists_returns200() throws Exception {
        UUID operationId = UUID.randomUUID();
        LedgerOperation operation = new LedgerOperation("idem-123", ReferenceType.DEPOSIT, "INV-001");

        LedgerEntry debitEntry = LedgerEntry.builder()
                .accountId(10L).entryType(EntryType.DEBIT)
                .amount(new BigDecimal("150.00")).currency(Currency.PEN)
                .operation(operation).build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .accountId(20L).entryType(EntryType.CREDIT)
                .amount(new BigDecimal("150.00")).currency(Currency.PEN)
                .operation(operation).build();

        LedgerEntryResponse debitResponse =
                new LedgerEntryResponse(1L, operationId, 10L, EntryType.DEBIT, new BigDecimal("150.00"),
                        Currency.PEN, ReferenceType.DEPOSIT, Instant.parse("2026-01-01T10:00:00Z"));

        LedgerEntryResponse creditResponse =
                new LedgerEntryResponse(2L, operationId, 20L, EntryType.CREDIT, new BigDecimal("150.00"),
                        Currency.PEN, ReferenceType.DEPOSIT, Instant.parse("2026-01-01T10:00:01Z"));

        OperationEntries result = new OperationEntries(operation, List.of(debitEntry, creditEntry));
        OperationDetailResponse response = new OperationDetailResponse(
                operationId,
                operation.getReferenceType(),
                operation.getReferenceId(),
                Instant.parse("2026-01-01T10:00:00Z"),
                List.of(debitResponse, creditResponse)
        );

        when(queryService.getOperationEntries(any(UUID.class))).thenReturn(result);
        when(operationDetailAssembler.toResponse(any(LedgerOperation.class), anyList())).thenReturn(response);

        mockMvc.perform(get("/entries/operations/{operationId}", operationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.operationId").value(operationId.toString()))
                .andExpect(jsonPath("$.referenceType").value("DEPOSIT"))
                .andExpect(jsonPath("$.referenceId").value("INV-001"))
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].id").value(1))
                .andExpect(jsonPath("$.entries[0].accountId").value(10))
                .andExpect(jsonPath("$.entries[0].entryType").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].amount").value(150.00))
                .andExpect(jsonPath("$.entries[1].id").value(2))
                .andExpect(jsonPath("$.entries[1].accountId").value(20))
                .andExpect(jsonPath("$.entries[1].entryType").value("CREDIT"))
                .andExpect(jsonPath("$.entries[1].amount").value(150.00));
    }

    // when(queryService.getEntryDetail(any(Long.class)))
    //                .thenThrow(new NotFoundException("Entry not found"));
    //
    @Test
    void getOperationDetail_whenOperationIsNotFound_returns404() throws Exception{
        UUID operationId = UUID.randomUUID();
        LedgerOperation operation = new LedgerOperation("idem-123", ReferenceType.DEPOSIT, "INV-001");

        when(queryService.getOperationEntries(any(UUID.class))).thenThrow(new NotFoundException("Operation not found and entries not found"));

        mockMvc.perform(get("/entries/operations/{operationId}", operationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        verifyNoInteractions(operationDetailAssembler);


    }
    @Test
    void getOperationDetail_whenOperationIdIsNotValidUUID_returns400() throws Exception {
        String invalidOperationId = "not-a-uuid";

        mockMvc.perform(get("/entries/operations/{operationId}", invalidOperationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);
        verifyNoInteractions(operationDetailAssembler);
    }











}
