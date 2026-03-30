package com.housing.billing.service;

import com.housing.billing.model.Tenant;
import com.housing.billing.repository.TenantRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;

import com.housing.billing.dto.request.GenerateInvoiceRequest;
import com.housing.billing.exception.ResourceNotFoundException;
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
    @Value("${app.billing.grace-days:15}")
    private int graceDays;  // default 15 if not provided
    private final TenantRepository tenantRepository;
    private final DynamicFilterEngine dynamicFilterEngine;

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
        if (existing.isPresent() && tenantId.equals(existing.get().getTenantId())) {
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

       //Use request-provided dates if present
        Instant issueDate = req.getIssueDate();
        Instant dueDate   = req.getDueDate();

        if (issueDate == null || dueDate == null) {
            // Load tenant to determine billingDay
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

            int billingDay = tenant.getBillingDay(); // expected 1–28

            // Defensive clamp
            if (billingDay < 1)  billingDay = 1;
            if (billingDay > 28) billingDay = 28;

            // Compute issueDate = YYYY-MM-billingDay @ 00:00Z
            LocalDate localIssue = LocalDate.of(req.getYear(), req.getMonth(), billingDay);
            Instant computedIssue = localIssue.atStartOfDay().toInstant(ZoneOffset.UTC);

            if (issueDate == null) {
                issueDate = computedIssue;
            }

            if (dueDate == null) {
                // dueDate = issueDate + graceDays
                dueDate = computedIssue.plus(graceDays, ChronoUnit.DAYS);
            }
        }

        invoice.setIssueDate(issueDate);
        invoice.setDueDate(dueDate);
        invoice.setType("invoice");
        invoice.setTenantId(tenantId);
        invoice.setCreatedAt(Instant.now());

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
        return invoiceRepository.findById(invoiceId)
                .filter(inv -> tenantId.equals(inv.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }

    // Called after a payment is recorded
    public Invoice recalculate(String tenantId, String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .filter(foundInvoice -> tenantId.equals(foundInvoice.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

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
