package com.housing.billing.service;

import com.housing.billing.model.Tenant;
import com.housing.billing.repository.TenantRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;

import com.housing.billing.dto.request.GenerateInvoiceRequest;
import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.exception.TenantIsolationException;
import com.housing.billing.exception.InvalidFilterSyntaxException;
import com.housing.billing.filter.DynamicFilterEngine;
import com.housing.billing.filter.FilterExpressionParser;
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
            return backfillExistingInvoiceOwnerIfMissing(existing.get(), tenantId, req.getUnitId());
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

        // Keep manual values if provided; auto-compute only for null fields.
        Instant resolvedIssueDate = req.getIssueDate();
        Instant resolvedDueDate = req.getDueDate();

        if (resolvedIssueDate == null) {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

            int preferredDay = tenant.getBillingDate() == null ? 1 : tenant.getBillingDate().getDayOfMonth();
            int maxDay = YearMonth.of(req.getYear(), req.getMonth()).lengthOfMonth();
            int issueDay = Math.min(preferredDay, maxDay);

            LocalDate localIssue = LocalDate.of(req.getYear(), req.getMonth(), issueDay);
            resolvedIssueDate = localIssue.atStartOfDay().toInstant(ZoneOffset.UTC);
        }

        if (resolvedDueDate == null) {
            resolvedDueDate = resolvedIssueDate.plus(paymentTermsDays, ChronoUnit.DAYS);
        }

        Instant now = Instant.now();
        String status = resolveInvoiceStatus(closing, payments, resolvedDueDate, now);
        invoice.setStatus(status);
        invoice.setPaymentDate("PAID".equals(status) ? now : null);

        invoice.setIssueDate(resolvedIssueDate);
        invoice.setDueDate(resolvedDueDate);
        invoice.setType("invoice");
        invoice.setTenantId(tenantId);
        invoice.setCreatedAt(now);
        modelValidationService.validate(invoice);

        return invoiceRepository.save(invoice);
    }

    public void backfillOwnerForUnitInvoices(String tenantId, String unitId, String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return;
        }

        List<Invoice> invoices = invoiceRepository.findByTenantIdAndUnitId(tenantId, unitId);
        for (Invoice invoice : invoices) {
            if (invoice.getOwnerId() != null && !invoice.getOwnerId().isBlank()) {
                continue;
            }


            invoice.setOwnerId(ownerId);
            invoice.setUpdatedAt(Instant.now());
            modelValidationService.validate(invoice);
            invoiceRepository.save(invoice);
        }
    }

    public List<Invoice> list(String tenantId, String filter) {
        List<Invoice> tenantScopedInvoices = invoiceRepository.findAllByTenantId(tenantId);
        validateInvoiceFilter(filter);
        return dynamicFilterEngine.apply(
                tenantScopedInvoices,
                filter,
                Invoice.class,
                FILTERABLE_FIELDS,
                FILTER_VALUE_NOT_FOUND_MESSAGES
        );
    }

    private void validateInvoiceFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return;
        }

        FilterExpressionParser parser = new FilterExpressionParser();
        FilterExpressionParser.Node root = parser.parse(filter);
        boolean hasMonth = containsFilterField(root, "month");
        boolean hasYear = containsFilterField(root, "year");

        if (hasMonth && !hasYear) {
            throw new InvalidFilterSyntaxException(
                    "Filter by month requires year (example: year==2026 && month==5)"
            );
        }
    }

    private boolean containsFilterField(FilterExpressionParser.Node node, String field) {
        if (node instanceof FilterExpressionParser.LogicalNode logicalNode) {
            return containsFilterField(logicalNode.left(), field)
                    || containsFilterField(logicalNode.right(), field);
        }

        FilterExpressionParser.ConditionNode condition = (FilterExpressionParser.ConditionNode) node;
        return field.equals(condition.field());
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

        Instant now = Instant.now();
        String previousStatus = invoice.getStatus();
        String newStatus = resolveInvoiceStatus(closing, invoice.getPaymentsInPeriod(), invoice.getDueDate(), now);

        invoice.setStatus(newStatus);
        if ("PAID".equals(newStatus)) {
            if (!"PAID".equals(previousStatus)) {
                invoice.setPaymentDate(now);
            }
        } else {
            invoice.setPaymentDate(null);
        }

        invoice.setUpdatedAt(now);
        return invoiceRepository.save(invoice);
    }

    private String resolveInvoiceStatus(BigDecimal closing, BigDecimal payments, Instant dueDate, Instant now) {
        if (closing.compareTo(BigDecimal.ZERO) <= 0) {
            return "PAID";
        }
        if (dueDate != null && now.isAfter(dueDate)) {
            return "OVERDUE";
        }
        if (payments.compareTo(BigDecimal.ZERO) > 0) {
            return "PARTIAL";
        }
        return "DUE";
    }

    private Invoice backfillExistingInvoiceOwnerIfMissing(Invoice invoice, String tenantId, String unitId) {
        if (invoice.getOwnerId() != null && !invoice.getOwnerId().isBlank()) {
            return invoice;
        }

        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));
        if (!tenantId.equals(unit.getTenantId())) {
            throw new TenantIsolationException("Tenant isolation violation");
        }

        if (unit.getOwnerId() == null || unit.getOwnerId().isBlank()) {
            return invoice;
        }

        invoice.setOwnerId(unit.getOwnerId());
        invoice.setUpdatedAt(Instant.now());
        modelValidationService.validate(invoice);
        return invoiceRepository.save(invoice);
    }
}
