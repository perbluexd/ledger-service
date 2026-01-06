package com.banca.ledger.api.controller;

import com.banca.ledger.api.dto.CreateLedgerEntryRequest;
import com.banca.ledger.api.dto.LedgerEntryResponse;
import com.banca.ledger.application.service.LedgerEntryCommandService;
import com.banca.ledger.domain.model.LedgerEntry;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/entries")
public class LedgerEntryController {
    private final LedgerEntryCommandService ledgerEntryCommandService;

    public LedgerEntryController(LedgerEntryCommandService ledgerEntryCommandService){
        this.ledgerEntryCommandService = ledgerEntryCommandService;
    }
    /*
    ResquestBody se usa principalmente para datos grados, json, post, put y patch, dtos completos
    Mientras que requestparam se usa principalmente para datos simples, query params, get y delete, filtros individuales
    Por otro lado pathvariable se usa con get para identificar recursos en la url y ademas en restfull

    Por otro lado, ResponseEntity nos permite personalizar la respuesta HTTP, incluyendo el código de estado,
    encabezados y el cuerpo de la respuesta. Es útil cuando necesitamos más control sobre la respuesta
    que simplemente devolver un objeto.
     */
    @PostMapping
    public ResponseEntity<LedgerEntryResponse> createEntry(@Valid @RequestBody CreateLedgerEntryRequest request){
        LedgerEntry saved = ledgerEntryCommandService.createEntry(request);
        LedgerEntryResponse response = toResponse(saved);
        return ResponseEntity.ok(response);

    }

    private LedgerEntryResponse toResponse(LedgerEntry entry){
        LedgerEntryResponse res = new LedgerEntryResponse();
        res.setId(entry.getId());
        res.setAccountId(entry.getAccountId());
        res.setEntryType(entry.getEntryType());
        res.setAmount(entry.getAmount());
        res.setCurrency(entry.getCurrency());
        res.setReferenceType(entry.getReferenceType());
        res.setCreatedAt(entry.getCreatedAt());
        return res;
    }

}
