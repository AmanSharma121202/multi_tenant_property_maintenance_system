package com.housing.billing.scheduler;

import com.housing.billing.model.Tenant;
import com.housing.billing.repository.TenantRepository;
import com.housing.billing.service.AsyncInvoiceGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceGenerationScheduler {

    private final TenantRepository tenantRepository;
    private final AsyncInvoiceGenerationService asyncInvoiceGenerationService;

    @Value("${app.async.invoice-generation.tenant-timezone:UTC}")
    private String tenantTimezone;

    @Scheduled(cron = "${app.async.invoice-generation.tenant-scan-cron:0 */1 * * * *}")
    public void scheduleTenantInvoices() {
        ZoneId zoneId = ZoneId.of(tenantTimezone);
        LocalDate today = LocalDate.now(zoneId);

        for (Tenant tenant : tenantRepository.findAllTenants()) {
            LocalDate invoiceDate = tenant.getInvoiceDate();
            if (invoiceDate == null || !invoiceDate.isEqual(today)) {
                continue;
            }

            asyncInvoiceGenerationService.scheduleTenantInvoiceGeneration(
                    tenant.getId(),
                    invoiceDate,
                    Duration.ofMinutes(1)
            );
            log.info("Scheduled tenant invoice generation for tenant={} after 1 minute", tenant.getId());
        }
    }
}

