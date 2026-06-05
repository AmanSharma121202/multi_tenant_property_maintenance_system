package com.housing.billing.scheduler;

import com.housing.billing.messaging.InvoiceFlowEventPublisher;
import com.housing.billing.messaging.TenantInvoiceDueEvent;
import com.housing.billing.model.Tenant;
import com.housing.billing.repository.TenantRepository;
import com.housing.billing.service.AsyncInvoiceGenerationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.time.DateTimeException;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceGenerationScheduler {

    private final TenantRepository tenantRepository;
    private final InvoiceFlowEventPublisher invoiceFlowEventPublisher;
    private final AsyncInvoiceGenerationService asyncInvoiceGenerationService;

    @Value("${app.async.invoice-generation.tenant-timezone:UTC}")
    private String tenantTimezone;

    @Value("${app.kafka.enabled:false}")
    private boolean kafkaEnabled;

    @Value("${app.async.invoice-generation.tenant-scan-cron:0 */1 * * * *}")
    private String tenantScanCron;

    @PostConstruct
    void logSchedulerConfig() {
        log.info("Invoice generation scheduler registered: cron='{}' timezone='{}' kafkaEnabled={}",
                tenantScanCron, tenantTimezone, kafkaEnabled);
    }

    @Scheduled(cron = "${app.async.invoice-generation.tenant-scan-cron:0 */1 * * * *}")
    public void scheduleTenantInvoices() {
        long tickStartedAtMs = System.currentTimeMillis();
        if (!kafkaEnabled) {
            log.warn("Kafka disabled; running tenant invoice generation in-process");
        }

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(tenantTimezone);
        } catch (DateTimeException ex) {
            log.error("Skipping tenant invoice scheduling due to invalid tenant timezone='{}': {}",
                    tenantTimezone, rootMessage(ex));
            return;
        }

        LocalDate today = LocalDate.now(zoneId);
        log.info("Tenant invoice scheduling tick started: date={} timezone={} kafkaEnabled={}",
                today, tenantTimezone, kafkaEnabled);

        List<Tenant> tenants;
        try {
            tenants = tenantRepository.findAllTenants();
        } catch (Exception ex) {
            log.error("Skipping tenant invoice scheduling for date={} because tenant query failed: {}",
                    today, rootMessage(ex));
            return;
        }

        int publishedCount = 0;
        int directDispatchCount = 0;
        int skippedNoAnchorCount = 0;
        int skippedNotDueCount = 0;
        int tenantFailureCount = 0;
        for (Tenant tenant : tenants) {
            try {
                Integer tenantBillingDay = tenant.getBillingDay();
                if (tenantBillingDay == null || tenantBillingDay == 0) {
                    skippedNoAnchorCount++;
                    log.info("Skipping tenant in scheduler: tenant={} reason=no-billing-day (set billing_day on tenant or restart after migration)",
                            tenant.getId());
                    continue;
                }

                // Compute effective billing day for this month (handle short months)
                int effectiveBillingDay = Math.min(tenantBillingDay, today.lengthOfMonth());
                if (today.getDayOfMonth() < effectiveBillingDay) {
                    skippedNotDueCount++;
                    log.info("Skipping tenant in scheduler: tenant={} reason=not-due-today billingDay={} todayDay={}",
                            tenant.getId(), effectiveBillingDay, today.getDayOfMonth());
                    continue;
                }

                // Generate invoice for the current billing cycle (same month as manual generation)
                LocalDate billingDate = YearMonth.from(today).atDay(1);

                TenantInvoiceDueEvent event = TenantInvoiceDueEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .tenantId(tenant.getId())
                        .billingDate(billingDate)
                        .delaySeconds(0)
                        .occurredAt(Instant.now())
                        .build();

                if (kafkaEnabled) {
                    invoiceFlowEventPublisher.publishTenantInvoiceDue(event);
                    publishedCount++;
                    log.info("Scheduler queued tenant invoice due event: eventId={} tenant={} cycle={}-{}",
                            event.getEventId(), tenant.getId(), today.getYear(), today.getMonthValue());
                } else {
                    asyncInvoiceGenerationService.scheduleTenantInvoiceGeneration(
                            tenant.getId(),
                            billingDate,
                            Duration.ZERO,
                            event.getEventId()
                    );
                    directDispatchCount++;
                    log.info("Scheduler dispatched tenant invoice generation in-process: flowId={} tenant={} cycle={}-{}",
                            event.getEventId(), tenant.getId(), today.getYear(), today.getMonthValue());
                }
            } catch (Exception ex) {
                tenantFailureCount++;
                log.error("Tenant invoice scheduling failed for tenant={} on cycle={}-{}: {}",
                        tenant == null ? "unknown" : tenant.getId(),
                        today.getYear(), today.getMonthValue(), rootMessage(ex));
            }
        }

        long tickDurationMs = System.currentTimeMillis() - tickStartedAtMs;
        log.info("Tenant invoice scheduling tick completed: date={} tenantsScanned={} eventsPublished={} directDispatches={} skippedNoAnchor={} skippedNotDue={} tenantFailures={} durationMs={}",
                today, tenants.size(), publishedCount, directDispatchCount, skippedNoAnchorCount, skippedNotDueCount,
                tenantFailureCount, tickDurationMs);
    }

    private String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        return (msg == null || msg.isBlank()) ? "Unknown scheduling failure" : msg;
    }
}
