package com.housing.billing.service;

import com.housing.billing.dto.request.RecordPaymentRequest;
import com.housing.billing.dto.request.UpdatePaymentRequest;
import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.filter.DynamicFilterEngine;
import com.housing.billing.model.Payment;
import com.housing.billing.repository.InvoiceRepository;
import com.housing.billing.repository.PaymentRepository;
import com.housing.billing.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final DynamicFilterEngine dynamicFilterEngine;

    private static final Set<String> FILTERABLE_FIELDS = Set.of(
            "method", "amount", "receivedAt", "txnRef"
    );

    private static final Map<String, String> FILTER_VALUE_NOT_FOUND_MESSAGES = Map.of(
            "method", "Payment not found for method '%s'",
            "amount", "Payment not found for amount '%s'",
            "receivedAt", "Payment not found for receivedAt '%s'",
            "txnRef", "Payment not found for txnRef '%s'"
    );

    public Payment record(String tenantId, RecordPaymentRequest req, String idempotencyKey) {
        // 1) Build a tenant + invoice scoped idempotency key
        String safeKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey.trim()
                : UUID.randomUUID().toString();

        String paymentId = "payment::" + tenantId + "::" + req.getInvoiceId() + "::" + safeKey;

        // 2) If a payment with this ID already exists, return it (idempotent behavior)
        var existing = paymentRepository.findById(paymentId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 3) Create & save new payment
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setTenantId(tenantId);
        payment.setInvoiceId(req.getInvoiceId());
        payment.setUnitId(req.getUnitId());
        payment.setOwnerId(req.getOwnerId());
        payment.setMethod(req.getMethod());
        payment.setAmount(req.getAmount());
        payment.setTxnRef(req.getTxnRef());
        payment.setNotes(req.getNotes());
        payment.setReceivedAt(Instant.now());
        payment.setType("payment");
        payment.setCreatedAt(Instant.now());

        Payment saved = paymentRepository.save(payment);

        // 4) Update invoice totals and recalc status (simple single-doc update)
        invoiceRepository.findById(req.getInvoiceId()).ifPresent(invoice -> {
            BigDecimal prior = invoice.getPaymentsInPeriod() == null
                    ? BigDecimal.ZERO
                    : invoice.getPaymentsInPeriod();
            BigDecimal total = prior.add(req.getAmount());
            invoice.setPaymentsInPeriod(total);
            invoice.setUpdatedAt(Instant.now());
            invoiceRepository.save(invoice);
            invoiceService.recalculate(tenantId, invoice.getId());
        });

        return saved;
    }

    public List<Payment> list(String tenantId, String filter) {
        List<Payment> tenantScopedPayments = paymentRepository.findAllByTenantId(tenantId);
        return dynamicFilterEngine.apply(
                tenantScopedPayments,
                filter,
                Payment.class,
                FILTERABLE_FIELDS,
                FILTER_VALUE_NOT_FOUND_MESSAGES
        );
    }

    public Payment get(String tenantId, String paymentId) {
        return paymentRepository.findById(paymentId)
                .filter(payment -> tenantId.equals(payment.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    public Payment update(String tenantId, String paymentId, UpdatePaymentRequest req) {
        Payment payment = get(tenantId, paymentId);
        if (req.getAmount() != null) payment.setAmount(req.getAmount());
        if (req.getMethod() != null) payment.setMethod(req.getMethod());
        if (req.getNotes()  != null) payment.setNotes(req.getNotes());
        if (req.getTxnRef() != null) payment.setTxnRef(req.getTxnRef());
        payment.setUpdatedAt(Instant.now());
        return paymentRepository.save(payment);
    }
}