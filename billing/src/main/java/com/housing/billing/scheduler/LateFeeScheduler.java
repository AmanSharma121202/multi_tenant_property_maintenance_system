package com.housing.billing.scheduler;

import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.model.Invoice;
import com.housing.billing.model.Tenant;
import com.housing.billing.repository.InvoiceRepository;
import com.housing.billing.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class LateFeeScheduler {

    private final InvoiceRepository invoiceRepository;
    private final TenantRepository tenantRepository;

    // Runs every day at midnight
    @Scheduled(cron = "0 0 0 * * *")
    public void applyLateFees() {
        log.info("Running late fee scheduler...");
        final Instant now = Instant.now();

        tenantRepository.findAllTenants().forEach(tenant -> {
            invoiceRepository.findOverdueByTenantId(tenant.getId())
                    .forEach(inv -> applyFeeForTenant(inv, tenant, now));
        });
    }

    private void applyFeeForTenant(Invoice invoice, Tenant tenant, Instant now) {
        if (invoice.getDueDate() == null) return;

        long overdueDays = ChronoUnit.DAYS.between(invoice.getDueDate(), now);
        if (overdueDays <= 0) return;

        // Safe defaults
        BigDecimal opening = nvl(invoice.getOpeningBalance());
        BigDecimal charges = nvl(invoice.getCurrentCharges());
        BigDecimal adjustments = nvl(invoice.getAdjustments());
        BigDecimal prevLate = nvl(invoice.getLateFee());
        BigDecimal payments = nvl(invoice.getPaymentsInPeriod());
        BigDecimal outstandingBeforeNewFee =
                opening.add(charges).add(adjustments).add(prevLate).subtract(payments);

        // Do not compute fee on negative/zero outstanding amounts
        if (outstandingBeforeNewFee.compareTo(BigDecimal.ZERO) <= 0) {
            updateStatusAndSave(invoice, outstandingBeforeNewFee, prevLate);
            return;
        }

        BigDecimal dailyFee; // fee for the overdueDays window, prorated
        String type = tenant.getLateFeeType() != null ? tenant.getLateFeeType().trim().toUpperCase() : "";
        double value = tenant.getLateFeeValue(); // percentage or fixed amount per month depending on type

        if ("PERCENTAGE".equals(type)) {
            // Monthly percentage rate, prorated by 30 days
            BigDecimal monthlyRate = BigDecimal.valueOf(value).movePointLeft(2); // e.g., 2.0 -> 0.02
            dailyFee = outstandingBeforeNewFee
                    .multiply(monthlyRate)
                    .divide(BigDecimal.valueOf(30), 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(overdueDays));
        } else if ("FIXED".equals(type)) {
            // Fixed monthly amount, prorated by 30 days
            dailyFee = BigDecimal.valueOf(value)
                    .divide(BigDecimal.valueOf(30), 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(overdueDays));
        } else {
            // Unknown/disabled late fee type -> no fee
            dailyFee = BigDecimal.ZERO;
        }

        // Round currency to 2 decimals
        dailyFee = dailyFee.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        // Update invoice fields
        BigDecimal newLateFee = prevLate.add(dailyFee);
        BigDecimal newClosing = opening.add(charges).add(adjustments).add(newLateFee).subtract(payments);

        invoice.setLateFee(newLateFee);
        invoice.setClosingBalance(newClosing);
        invoice.setUpdatedAt(Instant.now());

        // Recompute status (same rules used in your InvoiceService.recalculate)
        if (newClosing.compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setStatus("PAID");
        } else if (payments.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus("PARTIAL");
        } else {
            invoice.setStatus("OVERDUE");
        }

        invoiceRepository.save(invoice);
        log.debug("Applied late fee {} (type={}, value={}) to invoice {}", dailyFee, type, value, invoice.getId());
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private void updateStatusAndSave(Invoice invoice, BigDecimal closing, BigDecimal late) {
        invoice.setLateFee(late);
        invoice.setClosingBalance(closing);
        invoice.setUpdatedAt(Instant.now());
        if (closing.compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setStatus("PAID");
        } else {
            invoice.setStatus("OVERDUE");
        }
        invoiceRepository.save(invoice);
    }
}


