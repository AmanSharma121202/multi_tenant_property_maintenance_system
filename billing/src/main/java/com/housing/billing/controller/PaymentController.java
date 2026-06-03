package com.housing.billing.controller;

import com.housing.billing.dto.request.RecordPaymentRequest;
import com.housing.billing.dto.request.UpdatePaymentMetadataRequest;
import com.housing.billing.model.Payment;
import com.housing.billing.security.TenantIdNormalizer;
import com.housing.billing.service.PaymentService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tenants/{tenantId}/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<Payment> record(@PathVariable String tenantId,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                          @Valid @RequestBody RecordPaymentRequest req) {
        tenantId = TenantIdNormalizer.normalize(tenantId);
        return ResponseEntity.status(201).body(
                paymentService.record(tenantId, req, idempotencyKey));
    }

    @GetMapping
    public ResponseEntity<List<Payment>> list(
            @PathVariable String tenantId,
            @Parameter(
                    description = "Unified filter expression.",
                    example = "method==\"UPI\" && amount>=500"
            )
            @RequestParam(required = false) String filter) {
        tenantId = TenantIdNormalizer.normalize(tenantId);
        return ResponseEntity.ok(paymentService.list(tenantId, filter));
    }


    @GetMapping("/{paymentId}")
    public ResponseEntity<Payment> get(@PathVariable String tenantId,
                                       @PathVariable String paymentId) {
        tenantId = TenantIdNormalizer.normalize(tenantId);
        return ResponseEntity.ok(paymentService.get(tenantId, paymentId));
    }

    @PatchMapping("/{paymentId}")
    public ResponseEntity<Payment> update(@PathVariable String tenantId,
                                          @PathVariable String paymentId,
                                          @Valid @RequestBody UpdatePaymentMetadataRequest req) {
        tenantId = TenantIdNormalizer.normalize(tenantId);
        return ResponseEntity.ok(paymentService.update(tenantId, paymentId, req));
    }
}
