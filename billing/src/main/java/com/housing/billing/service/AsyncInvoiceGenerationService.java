package com.housing.billing.service;

import com.housing.billing.dto.request.GenerateInvoiceRequest;
import com.housing.billing.repository.InvoiceRepository;
import com.housing.billing.repository.UnitRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AsyncInvoiceGenerationService {

    private final UnitRepository unitRepository;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final Executor invoiceGenerationExecutor;

    public AsyncInvoiceGenerationService(UnitRepository unitRepository,
                                         InvoiceService invoiceService,
                                         InvoiceRepository invoiceRepository,
                                         @Qualifier("invoiceGenerationExecutor") Executor invoiceGenerationExecutor) {
        this.unitRepository = unitRepository;
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.invoiceGenerationExecutor = invoiceGenerationExecutor;
    }

    public void scheduleTenantInvoiceGeneration(String tenantId, LocalDate invoiceDate, Duration delay) {
        scheduleTenantInvoiceGeneration(tenantId, invoiceDate, delay, null);
    }

    public void scheduleTenantInvoiceGeneration(String tenantId, LocalDate invoiceDate, Duration delay, String flowId) {
        String safeFlowId = (flowId == null || flowId.isBlank()) ? "n/a" : flowId;
        log.info("Scheduling tenant invoice generation task: flowId={} tenant={} billingDate={} delayMs={}",
                safeFlowId, tenantId, invoiceDate, delay.toMillis());

        CompletableFuture.runAsync(
                () -> generateForTenantUnits(tenantId, invoiceDate, safeFlowId),
                CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS, invoiceGenerationExecutor)
        ).exceptionally(ex -> {
            log.error("Tenant invoice generation task crashed: flowId={} tenant={} billingDate={} reason={}",
                    safeFlowId, tenantId, invoiceDate, rootMessage(ex));
            return null;
        });
    }

    private void generateForTenantUnits(String tenantId, LocalDate invoiceDate) {
        generateForTenantUnits(tenantId, invoiceDate, "n/a");
    }

    private void generateForTenantUnits(String tenantId, LocalDate invoiceDate, String flowId) {
        long startedAtMs = System.currentTimeMillis();
        List<String> unitIds;
        try {
            unitIds = unitRepository.findByTenantIdAndActive(tenantId, true).stream()
                    .map(unit -> unit.getId())
                    .toList();
        } catch (Exception ex) {
            log.error("Tenant invoice generation failed before unit traversal: flowId={} tenant={} billingDate={} reason={}",
                    flowId, tenantId, invoiceDate, rootMessage(ex));
            return;
        }

        if (unitIds.isEmpty()) {
            log.warn("Tenant invoice generation skipped: flowId={} tenant={} billingDate={} reason=no-active-units",
                    flowId, tenantId, invoiceDate);
            return;
        }

        log.info("Starting tenant invoice generation: flowId={} tenant={} billingDate={} unitCount={}",
                flowId, tenantId, invoiceDate, unitIds.size());

        int successCount = 0;
        int failureCount = 0;
        int skippedExistingCount = 0;

        for (String unitId : unitIds) {
            try {
                boolean alreadyGenerated = !invoiceRepository
                        .findAnyByTenantIdAndUnitIdAndYearAndMonth(tenantId, unitId, invoiceDate.getYear(), invoiceDate.getMonthValue())
                        .isEmpty();
                if (alreadyGenerated) {
                    skippedExistingCount++;
                    log.debug("Tenant invoice generation skipped: flowId={} tenant={} unit={} cycle={}-{} reason=already-exists",
                            flowId, tenantId, unitId, invoiceDate.getYear(), invoiceDate.getMonthValue());
                    continue;
                }

                GenerateInvoiceRequest req = new GenerateInvoiceRequest();
                req.setUnitId(unitId);
                req.setYear(invoiceDate.getYear());
                req.setMonth(invoiceDate.getMonthValue());
                req.setIssueDate(invoiceDate.atStartOfDay().toInstant(ZoneOffset.UTC));
                invoiceService.generate(tenantId, req);
                successCount++;
                log.info("Tenant invoice generation succeeded: flowId={} tenant={} unit={} cycle={}-{}",
                        flowId, tenantId, unitId, invoiceDate.getYear(), invoiceDate.getMonthValue());
            } catch (Exception ex) {
                failureCount++;
                log.warn("Tenant invoice generation failed: flowId={} tenant={} unit={} cycle={}-{} reason={}",
                        flowId, tenantId, unitId, invoiceDate.getYear(), invoiceDate.getMonthValue(), rootMessage(ex));
            }
        }

        long durationMs = System.currentTimeMillis() - startedAtMs;
        log.info("Tenant invoice generation completed: flowId={} tenant={} billingDate={} unitCount={} successCount={} skippedExistingCount={} failureCount={} durationMs={}",
                flowId, tenantId, invoiceDate, unitIds.size(), successCount, skippedExistingCount, failureCount, durationMs);
    }

    private String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        return (msg == null || msg.isBlank()) ? "Invoice generation failed" : msg;
    }
}
