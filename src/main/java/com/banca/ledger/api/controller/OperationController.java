package com.banca.ledger.api.controller;

import com.banca.ledger.api.dto.OperationDetailResponse;
import com.banca.ledger.api.mapper.OperationDetailAssembler;
import com.banca.ledger.application.service.LedgerEntryCommandService;
import com.banca.ledger.application.service.LedgerEntryQueryService;
import com.banca.ledger.application.service.OperationEntries;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/operations")
@Validated
public class OperationController {

    private final OperationDetailAssembler operationDetailAssembler;
    private final LedgerEntryQueryService ledgerEntryQueryService;
    private final LedgerEntryCommandService ledgerEntryCommandService;

    public OperationController(
            OperationDetailAssembler operationDetailAssembler,
            LedgerEntryQueryService ledgerEntryQueryService,
            LedgerEntryCommandService ledgerEntryCommandService
    ) {
        this.operationDetailAssembler = operationDetailAssembler;
        this.ledgerEntryQueryService = ledgerEntryQueryService;
        this.ledgerEntryCommandService = ledgerEntryCommandService;
    }

    // UC-7: obtener operación por idempotencyKey
    @GetMapping("/{idempotencyKey}")
    public ResponseEntity<OperationDetailResponse> getByIdempotencyKey(
            @PathVariable
            @NotBlank(message = "idempotencyKey no puede ser vacío")
            String idempotencyKey
    ) {
        OperationEntries result =
                ledgerEntryQueryService.getOperationEntriesByIdempotencyKey(idempotencyKey);

        OperationDetailResponse response =
                operationDetailAssembler.toResponse(result.operation(), result.entries());

        return ResponseEntity.ok(response);
    }

    // UC-8: reversa de operación
    @PostMapping("/{operationId}")
    public ResponseEntity<OperationDetailResponse> saveReversed(
            @PathVariable
            @NotNull(message = "operationId es requerido")
            UUID operationId
    ) {
        OperationEntries result = ledgerEntryCommandService.reverseOperation(operationId);

        OperationDetailResponse response =
                operationDetailAssembler.toResponse(result.operation(), result.entries());

        return ResponseEntity.ok(response);
    }
}
