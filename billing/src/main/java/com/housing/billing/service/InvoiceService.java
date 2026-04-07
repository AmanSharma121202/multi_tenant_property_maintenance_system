package com.housing.billing.service;

import com.housing.billing.model.Tenant;
import com.housing.billing.repository.TenantRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;

import com.housing.billing.dto.request.GenerateInvoiceRequest;
import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.exception.TenantIsolationException;
import com.housing.billing.filter.DynamicFilterEngine;
import com.housing.billing.model.Invoice;
import com.housing.billing.model.Profile;
import com.housing.billing.model.Unit;
import com.housing.billing.repository.InvoiceRepository;
import com.housing.billing.repository.ProfileRepository;
import com.housing.billing.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final UnitRepository unitRepository;
    private final ProfileRepository profileRepository;
    @Value("${app.billing.payment-terms-days:${app.billing.grace-days:15}}")
    private int paymentTermsDays;  // backward compatible with app.billing.grace-days
    private final TenantRepository tenantRepository;
    private final DynamicFilterEngine dynamicFilterEngine;
    private final ModelValidationService modelValidationService;

    private static final Set<String> FILTERABLE_FIELDS = Set.of(
            "year", "month", "status", "issueDate", "dueDate"
    );

    private static final Map<String, String> FILTER_VALUE_NOT_FOUND_MESSAGES = Map.ofEntries(
            Map.entry("year", "Invoice not found for year '%s'"),
            Map.entry("month", "Invoice not found for month '%s'"),
            Map.entry("status", "Invoice not found for status '%s'"),
            Map.entry("issueDate", "Invoice not found for issueDate '%s'"),
            Map.entry("dueDate", "Invoice not found for dueDate '%s'")
    );


    public Invoice generate(String tenantId, GenerateInvoiceRequest req) {

        // 1. Build the natural key — this makes generation idempotent
        //    If you call generate twice for the same unit+month, it won't duplicate
        String invoiceId = String.format("INV-%s-%d%02d",
                req.getUnitId(), req.getYear(), req.getMonth());

        // 2. If already generated, return existing invoice
        Optional<Invoice> existing = invoiceRepository.findById(invoiceId);
        if (existing.isPresent()) {
            if (!tenantId.equals(existing.get().getTenantId())) {
                throw new TenantIsolationException("Tenant isolation violation");
            }
            return existing.get();
        }

        // 3. Get the previous month's closing balance = this month's opening balance
        int prevMonth = req.getMonth() == 1 ? 12 : req.getMonth() - 1;
        int prevYear  = req.getMonth() == 1 ? req.getYear() - 1 : req.getYear();
        String prevId = String.format("INV-%s-%d%02d", req.getUnitId(), prevYear, prevMonth);

        BigDecimal openingBalance = invoiceRepository.findById(prevId)
                .map(Invoice::getClosingBalance)
                .orElse(BigDecimal.ZERO);  // First ever invoice has zero opening balance

        // 4. Get monthly charge amount from the unit's assigned profile
        Unit unit = unitRepository.findById(req.getUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));
        if (!tenantId.equals(unit.getTenantId())) {
            throw new TenantIsolationException("Tenant isolation violation");
        }
        Profile profile = profileRepository.findByTenantIdAndCode(tenantId, unit.getProfileCode())
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        // 5. Build the invoice
        BigDecimal charges    = profile.getMonthlyAmount();
        BigDecimal adjustments = BigDecimal.ZERO;
        BigDecimal lateFee    = BigDecimal.ZERO;   // Applied later by scheduler
        BigDecimal payments   = BigDecimal.ZERO;   // No payments yet
        BigDecimal closing    = openingBalance.add(charges)
                .add(adjustments)
                .add(lateFee)
                .subtract(payments);

        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setTenantId(tenantId);
        invoice.setUnitId(req.getUnitId());
        invoice.setOwnerId(unit.getOwnerId());
        invoice.setYear(req.getYear());
        invoice.setMonth(req.getMonth());
        invoice.setOpeningBalance(openingBalance);
        invoice.setCurrentCharges(charges);
        invoice.setLateFee(lateFee);
        invoice.setAdjustments(adjustments);
        invoice.setPaymentsInPeriod(payments);
        invoice.setClosingBalance(closing);
        invoice.setStatus(closing.compareTo(BigDecimal.ZERO) <= 0 ? "PAID" : "OVERDUE");

        // Keep manual values if provided; auto-compute only for null fields.
        Instant resolvedIssueDate = req.getIssueDate();
        Instant resolvedDueDate = req.getDueDate();

        if (resolvedIssueDate == null) {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

            int billingDay = tenant.getBillingDay(); // expected 1-28
            if (billingDay < 1) billingDay = 1;
            if (billingDay > 28) billingDay = 28;

            LocalDate localIssue = LocalDate.of(req.getYear(), req.getMonth(), billingDay);
            resolvedIssueDate = localIssue.atStartOfDay().toInstant(ZoneOffset.UTC);
        }

        if (resolvedDueDate == null) {
            resolvedDueDate = resolvedIssueDate.plus(paymentTermsDays, ChronoUnit.DAYS);
        }

        invoice.setIssueDate(resolvedIssueDate);
        invoice.setDueDate(resolvedDueDate);
        invoice.setType("invoice");
        invoice.setTenantId(tenantId);
        invoice.setCreatedAt(Instant.now());
        modelValidationService.validate(invoice);

        return invoiceRepository.save(invoice);
    }

    public List<Invoice> list(String tenantId, String filter) {
        List<Invoice> tenantScopedInvoices = invoiceRepository.findAllByTenantId(tenantId);
        return dynamicFilterEngine.apply(
                tenantScopedInvoices,
                filter,
                Invoice.class,
                FILTERABLE_FIELDS,
                FILTER_VALUE_NOT_FOUND_MESSAGES
        );
    }

    public Invoice get(String tenantId, String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        if (!tenantId.equals(invoice.getTenantId())) {
            throw new TenantIsolationException("Tenant isolation violation");
        }

        return invoice;
    }

    // Called after a payment is recorded
    public Invoice recalculate(String tenantId, String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        if (!tenantId.equals(invoice.getTenantId())) {
            throw new TenantIsolationException("Tenant isolation violation");
        }

        BigDecimal closing = invoice.getOpeningBalance()
                .add(invoice.getCurrentCharges())
                .add(invoice.getAdjustments())
                .add(invoice.getLateFee())
                .subtract(invoice.getPaymentsInPeriod());

        invoice.setClosingBalance(closing);

        if (closing.compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setStatus("PAID");
        } else if (invoice.getPaymentsInPeriod().compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus("PARTIAL");
        } else {
            invoice.setStatus("OVERDUE");
        }

        invoice.setUpdatedAt(Instant.now());
        return invoiceRepository.save(invoice);
    }
}
