package com.housing.billing.controller;

import com.housing.billing.dto.request.GenerateTenantInvoicesRequest;
import com.housing.billing.dto.response.TenantInvoiceGenerationResult;
import com.housing.billing.messaging.InvoiceFlowEventPublisher;
import lombok.extern.slf4j.Slf4j;
import com.housing.billing.messaging.TenantInvoiceDueEvent;
import com.housing.billing.model.Invoice;
import com.housing.billing.service.AsyncInvoiceGenerationService;
import com.housing.billing.service.InvoiceService;
import com.housing.billing.security.TenantIdNormalizer;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/tenants/{tenantId}/invoices")
@RequiredArgsConstructor
public class InvoiceController {
    private final InvoiceService invoiceService;
    private final InvoiceFlowEventPublisher invoiceFlowEventPublisher;
    private final AsyncInvoiceGenerationService asyncInvoiceGenerationService;

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
        tenantId = TenantIdNormalizer.normalize(tenantId);
        return ResponseEntity.ok(invoiceService.list(tenantId, filter));
    }

    @GetMapping("/{invoiceId}")
    public ResponseEntity<Invoice> get(@PathVariable String tenantId,
                                       @PathVariable String invoiceId) {
        tenantId = TenantIdNormalizer.normalize(tenantId);
        return ResponseEntity.ok(invoiceService.get(tenantId, invoiceId));
    }

    @PostMapping("/{invoiceId}:recalculate")
    public ResponseEntity<Invoice> recalculate(@PathVariable String tenantId,
                                               @PathVariable String invoiceId) {
        tenantId = TenantIdNormalizer.normalize(tenantId);
        return ResponseEntity.ok(invoiceService.recalculate(tenantId, invoiceId));
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generateTenantInvoices(
            @PathVariable String tenantId,
            @Valid @RequestBody GenerateTenantInvoicesRequest request) {
        tenantId = TenantIdNormalizer.normalize(tenantId);
        YearMonth target = YearMonth.of(request.getYear(), request.getMonth());
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        if (target.isAfter(current)) {
            throw new IllegalArgumentException("year/month must not be in the future");
        }

        LocalDate invoiceDate = target.atDay(1);
        String flowId = "manual-" + UUID.randomUUID();

        TenantInvoiceGenerationResult result = asyncInvoiceGenerationService.scheduleTenantInvoiceGeneration(
                tenantId,
                invoiceDate,
                Duration.ZERO,
                flowId,
                request.getUnitId()
        );

        if (kafkaEnabled) {
            try {
                TenantInvoiceDueEvent event = TenantInvoiceDueEvent.builder()
                        .eventId(flowId)
                        .tenantId(tenantId)
                        .unitId(request.getUnitId())
                        .billingDate(invoiceDate)
                        .delaySeconds(0L)
                        .occurredAt(Instant.now())
                        .build();
                invoiceFlowEventPublisher.publishTenantInvoiceDue(event);
            } catch (Exception ex) {
                log.warn("Manual invoice generation succeeded but Kafka publish failed: flowId={} tenant={} reason={}",
                        flowId, tenantId, ex.getMessage());
            }
        }

        Map<String, String> response = new java.util.HashMap<>();
        response.put("message", buildGenerationMessage(result));
        response.put("failed", String.valueOf(result.failed()));
        if (result.failed() > 0) {
            response.put("message",
                    buildGenerationMessage(result) + " (" + result.failed() + " unit(s) failed — check server logs)");
        }
        response.put("tenantId", tenantId);
        response.put("year", String.valueOf(request.getYear()));
        response.put("month", String.valueOf(request.getMonth()));
        response.put("flowId", flowId);
        response.put("created", String.valueOf(result.created()));
        response.put("skipped", String.valueOf(result.skipped()));
        response.put("units", String.valueOf(result.units()));
        if (request.getUnitId() != null && !request.getUnitId().isBlank()) {
            response.put("unitId", request.getUnitId());
        }

        return ResponseEntity.ok(response);
    }

    private static String buildGenerationMessage(TenantInvoiceGenerationResult result) {
        if (result.units() == 0) {
            return "No active units found for invoice generation";
        }
        if (result.created() == 0 && result.skipped() > 0) {
            return "All " + result.skipped() + " invoice(s) already exist for this period";
        }
        if (result.created() > 0 && result.skipped() > 0) {
            return "Created " + result.created() + " invoice(s), skipped " + result.skipped() + " existing";
        }
        return "Created " + result.created() + " invoice(s)";
    }
}
