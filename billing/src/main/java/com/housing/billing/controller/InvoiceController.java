package com.housing.billing.controller;

import com.housing.billing.dto.request.GenerateInvoiceRequest;
import com.housing.billing.dto.response.InvoiceGenerationJobResponse;
import com.housing.billing.model.Invoice;
import com.housing.billing.service.AsyncInvoiceGenerationService;
import com.housing.billing.service.InvoiceService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tenants/{tenantId}/invoices")
@RequiredArgsConstructor
public class InvoiceController {
    private final AsyncInvoiceGenerationService asyncInvoiceGenerationService;
    private final InvoiceService invoiceService;

    @PostMapping(":generate")
    public ResponseEntity<InvoiceGenerationJobResponse> generate(@PathVariable String tenantId,
                                                                 @Valid @RequestBody GenerateInvoiceRequest req) {
        InvoiceGenerationJobResponse response = asyncInvoiceGenerationService.enqueue(tenantId, req);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<InvoiceGenerationJobResponse> getJobStatus(@PathVariable String tenantId,
                                                                     @PathVariable String jobId) {
        return ResponseEntity.ok(asyncInvoiceGenerationService.getStatus(tenantId, jobId));
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
