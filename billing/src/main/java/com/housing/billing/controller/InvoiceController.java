package com.housing.billing.controller;

import com.housing.billing.dto.request.GenerateTenantInvoicesRequest;
import com.housing.billing.messaging.InvoiceFlowEventPublisher;
import com.housing.billing.messaging.TenantInvoiceDueEvent;
import com.housing.billing.model.Invoice;
import com.housing.billing.service.InvoiceService;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/tenants/{tenantId}/invoices")
@RequiredArgsConstructor
public class InvoiceController {
    private final InvoiceService invoiceService;
    private final InvoiceFlowEventPublisher invoiceFlowEventPublisher;

    @Value("${app.kafka.enabled:false}")
    private boolean kafkaEnabled;

    @GetMapping
    public ResponseEntity<List<Invoice>> list(
            @PathVariable String tenantId,
            @Parameter(
                    description = "Unified filter expression.",
                    example = "year==2026 && month==1"
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

    @PostMapping(":generate")
    public ResponseEntity<Map<String, String>> generateTenantInvoices(
            @PathVariable String tenantId,
            @Valid @RequestBody GenerateTenantInvoicesRequest request) {
        if (!kafkaEnabled) {
            throw new IllegalArgumentException("Kafka is disabled; enable app.kafka.enabled to generate invoices");
        }

        YearMonth target = YearMonth.of(request.getYear(), request.getMonth());
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        if (target.isAfter(current)) {
            throw new IllegalArgumentException("year/month must not be in the future");
        }

        LocalDate invoiceDate = target.atDay(1);
        String flowId = "manual-" + UUID.randomUUID();
        TenantInvoiceDueEvent event = TenantInvoiceDueEvent.builder()
                .eventId(flowId)
                .tenantId(tenantId)
                .unitId(request.getUnitId())
                .billingDate(invoiceDate)
                .delaySeconds(0L)
                .occurredAt(Instant.now())
                .build();
        invoiceFlowEventPublisher.publishTenantInvoiceDue(event);

        Map<String, String> response = new java.util.HashMap<>();
        response.put("message", "Tenant invoice generation event published");
        response.put("tenantId", tenantId);
        response.put("year", String.valueOf(request.getYear()));
        response.put("month", String.valueOf(request.getMonth()));
        response.put("flowId", flowId);
        if (request.getUnitId() != null && !request.getUnitId().isBlank()) {
            response.put("unitId", request.getUnitId());
        }

        return ResponseEntity.accepted().body(response);
    }
}
