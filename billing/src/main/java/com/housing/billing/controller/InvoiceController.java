package com.housing.billing.controller;

import com.housing.billing.dto.request.GenerateInvoiceRequest;
import com.housing.billing.model.Invoice;
import com.housing.billing.service.InvoiceService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tenants/{tenantId}/invoices")
@RequiredArgsConstructor
public class InvoiceController {
    private final InvoiceService invoiceService;

    @PostMapping(":generate")
    public ResponseEntity<Invoice> generate(@PathVariable String tenantId,
                                            @Valid @RequestBody GenerateInvoiceRequest req) {
        return ResponseEntity.status(201).body(invoiceService.generate(tenantId, req));
    }

    @GetMapping
    public ResponseEntity<List<Invoice>> list(
            @PathVariable String tenantId,
            @Parameter(
                    description = "Unified filter expression. Example: status==\"PAID\" && month==1",
                    example = "status==\"PAID\" && month==1"
            )
            @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(invoiceService.list(tenantId, filter));
    }

    @GetMapping("/{invoiceId}")
    public ResponseEntity<Invoice> get(@PathVariable String tenantId,
                                       @PathVariable String invoiceId) {
        return ResponseEntity.ok(invoiceService.get(tenantId, invoiceId));
    }

    @PostMapping("/{invoiceId}:recalculate")
    public ResponseEntity<Invoice> recalculate(@PathVariable String tenantId,
                                               @PathVariable String invoiceId) {
        return ResponseEntity.ok(invoiceService.recalculate(tenantId, invoiceId));
    }
}
