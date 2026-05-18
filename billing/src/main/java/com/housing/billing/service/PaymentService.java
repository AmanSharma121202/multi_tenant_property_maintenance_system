package com.housing.billing.service;

import com.housing.billing.dto.request.RecordPaymentRequest;
import com.housing.billing.dto.request.UpdatePaymentMetadataRequest;
import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.exception.TenantIsolationException;
import com.housing.billing.filter.DynamicFilterEngine;
import com.housing.billing.model.Invoice;
import com.housing.billing.model.Payment;
import com.housing.billing.model.Unit;
import com.housing.billing.repository.InvoiceRepository;
import com.housing.billing.repository.PaymentRepository;
import com.housing.billing.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
    private final UnitRepository unitRepository;
    private final DynamicFilterEngine dynamicFilterEngine;
    private final ModelValidationService modelValidationService;

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

        Invoice invoice = invoiceService.get(tenantId, req.getInvoiceId());
        if (invoice.getOwnerId() == null || invoice.getOwnerId().isBlank()) {
            throw new IllegalStateException("Cannot record payment: invoice is not linked to an owner");
        }

        // 3) Create & save new payment
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setTenantId(tenantId);
        payment.setInvoiceId(req.getInvoiceId());
        payment.setUnitId(invoice.getUnitId());
        payment.setOwnerId(invoice.getOwnerId());
        payment.setMethod(req.getMethod());
        payment.setAmount(req.getAmount());
        payment.setTxnRef(req.getTxnRef());
        payment.setNotes(req.getNotes());
        payment.setPaidBy(req.getPaidBy());
        payment.setReceivedAt(Instant.now());
        payment.setType("payment");
        payment.setCreatedAt(Instant.now());
        modelValidationService.validate(payment);

        Payment saved = paymentRepository.save(payment);

        applyPaymentToUnitBalance(tenantId, invoice, req.getAmount());

        return saved;
    }

    private void applyPaymentToUnitBalance(String tenantId, Invoice targetInvoice, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return;
        }
        Unit unit = loadUnitForPayment(tenantId, targetInvoice.getUnitId());
        BigDecimal current = unit.getUnitBalance() == null ? BigDecimal.ZERO : unit.getUnitBalance();
        BigDecimal available = current.add(amount);
        BigDecimal remaining = settleOutstandingInvoices(tenantId, targetInvoice, available);
        unit.setTotalBalance(available);
        unit.setUnitBalance(remaining);
        unit.setUpdatedAt(Instant.now());
        modelValidationService.validate(unit);
        unitRepository.save(unit);
    }

    private Unit loadUnitForPayment(String tenantId, String unitId) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));
        if (!tenantId.equals(unit.getTenantId())) {
            throw new TenantIsolationException("Tenant isolation violation");
        }
        return unit;
    }

    private BigDecimal settleOutstandingInvoices(String tenantId, Invoice targetInvoice, BigDecimal available) {
        BigDecimal remaining = available;
        List<Invoice> outstanding = resolveOutstandingInvoices(tenantId, targetInvoice.getUnitId());
        List<Invoice> ordered = new ArrayList<>();
        if (isPayable(targetInvoice)) {
            ordered.add(targetInvoice);
        }
        for (Invoice candidate : outstanding) {
            if (candidate.getId() != null && candidate.getId().equals(targetInvoice.getId())) {
                continue;
            }
            ordered.add(candidate);
        }
        for (Invoice target : ordered) {
            if (remaining.signum() <= 0) {
                break;
            }
            if (!isPayable(target)) {
                continue;
            }
            BigDecimal closing = target.getClosingBalance() == null ? BigDecimal.ZERO : target.getClosingBalance();
            if (remaining.compareTo(closing) < 0) {
                break;
            }
            BigDecimal prior = target.getPaymentsInPeriod() == null ? BigDecimal.ZERO : target.getPaymentsInPeriod();
            target.setPaymentsInPeriod(prior.add(closing));
            target.setUpdatedAt(Instant.now());
            invoiceRepository.save(target);
            invoiceService.recalculate(tenantId, target.getId());
            remaining = remaining.subtract(closing);
        }
        return remaining;
    }

    private boolean isPayable(Invoice invoice) {
        if (invoice == null) {
            return false;
        }
        BigDecimal closing = invoice.getClosingBalance() == null ? BigDecimal.ZERO : invoice.getClosingBalance();
        return closing.signum() > 0 && invoice.getStatus() != null && !"PAID".equals(invoice.getStatus());
    }

    private List<Invoice> resolveOutstandingInvoices(String tenantId, String unitId) {
        List<Invoice> invoices = invoiceRepository.findByTenantIdAndUnitId(tenantId, unitId);
        return invoices.stream()
                .filter(other -> other.getStatus() != null && !"PAID".equals(other.getStatus()))
                .sorted((a, b) -> {
                    int yearCompare = Integer.compare(a.getYear(), b.getYear());
                    if (yearCompare != 0) {
                        return yearCompare;
                    }
                    return Integer.compare(a.getMonth(), b.getMonth());
                })
                .toList();
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
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (!tenantId.equals(payment.getTenantId())) {
            throw new TenantIsolationException("Tenant isolation violation");
        }

        return payment;
    }

    public Payment update(String tenantId, String paymentId, UpdatePaymentMetadataRequest req) {
        Payment payment = get(tenantId, paymentId);

        if (req.getTxnRef() != null) payment.setTxnRef(req.getTxnRef());
        if (req.getReceivedAt() != null) payment.setReceivedAt(req.getReceivedAt());
        if (req.getNotes() != null) payment.setNotes(req.getNotes());
        if (req.getPaidBy() != null) payment.setPaidBy(req.getPaidBy());

        payment.setUpdatedAt(Instant.now());
        modelValidationService.validate(payment);
        return paymentRepository.save(payment);
    }
}
