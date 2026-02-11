package com.banca.ledger.api.controller;

import com.banca.ledger.api.dto.CreateCompositeLedgerMovementRequest;
import com.banca.ledger.api.dto.CreateLedgerEntryRequest;
import com.banca.ledger.api.dto.LedgerEntryResponse;
import com.banca.ledger.api.dto.OperationDetailResponse;
import com.banca.ledger.api.mapper.LedgerEntryMapper;
import com.banca.ledger.api.mapper.OperationDetailAssembler;
import com.banca.ledger.application.service.LedgerEntryCommandService;
import com.banca.ledger.application.service.LedgerEntryQueryService;
import com.banca.ledger.application.service.OperationEntries;
import com.banca.ledger.domain.model.LedgerEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/entries")
@Validated
public class LedgerEntryController {

    private final LedgerEntryCommandService commandService;
    private final LedgerEntryQueryService queryService;
    private final LedgerEntryMapper ledgerEntryMapper;
    private final OperationDetailAssembler operationDetailAssembler;

    public LedgerEntryController(
            LedgerEntryCommandService commandService,
            LedgerEntryQueryService queryService,
            LedgerEntryMapper ledgerEntryMapper,
            OperationDetailAssembler operationDetailAssembler
    ) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.ledgerEntryMapper = ledgerEntryMapper;
        this.operationDetailAssembler = operationDetailAssembler;
    }

    // UC-1: crear entry
    @PostMapping
    public ResponseEntity<LedgerEntryResponse> createEntry(
            @Valid @RequestBody CreateLedgerEntryRequest request
    ) {
        LedgerEntry saved = commandService.createEntry(request);
        return ResponseEntity.ok(ledgerEntryMapper.toResponse(saved));
    }

    // UC-3: listar entries por accountId
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<Page<LedgerEntryResponse>> listEntries(
            @PathVariable
            @NotNull(message = "accountId es requerido")
            @Positive(message = "accountId debe ser válido")
            Long accountId,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page debe ser >= 0")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size debe estar entre 1 y 100")
            @Max(value = 100, message = "size debe estar entre 1 y 100")
            int size
    ) {
        Page<LedgerEntry> result = queryService.listEntries(accountId, page, size);
        return ResponseEntity.ok(result.map(ledgerEntryMapper::toResponse));
    }

    // UC-2: movimiento compuesto
    @PostMapping("/composite")
    public ResponseEntity<OperationDetailResponse> createCompositeMovement(
            @Valid @RequestBody CreateCompositeLedgerMovementRequest request
    ) {
        OperationEntries result = commandService.recordCompositeMovement(request);

        OperationDetailResponse response = operationDetailAssembler.toResponse(
                result.operation(),
                result.entries()
        );

        return ResponseEntity.ok(response);
    }

    // UC-4B: detalle por entryId
    @GetMapping("/{entryId}")
    public ResponseEntity<LedgerEntryResponse> getEntryDetail(
            @PathVariable
            @NotNull(message = "entryId es requerido")
            @Positive(message = "entryId debe ser válido")
            Long entryId
    ) {
        LedgerEntry entry = queryService.getEntryDetail(entryId);
        return ResponseEntity.ok(ledgerEntryMapper.toResponse(entry));
    }

    // UC-4A: detalle por operationId
    @GetMapping("/operations/{operationId}")
    public ResponseEntity<OperationDetailResponse> getOperationDetail(
            @PathVariable @NotNull(message = "operationId es requerido") UUID operationId
    ) {
        OperationEntries result = queryService.getOperationEntries(operationId);

        OperationDetailResponse response = operationDetailAssembler.toResponse(
                result.operation(),
                result.entries()
        );

        return ResponseEntity.ok(response);
    }
}
