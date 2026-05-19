package com.housing.billing.service;

import com.housing.billing.dto.request.GenerateInvoiceRequest;
import com.housing.billing.model.Unit;
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
        scheduleTenantInvoiceGeneration(tenantId, invoiceDate, delay, null, null);
    }

    public void scheduleTenantInvoiceGeneration(String tenantId, LocalDate invoiceDate, Duration delay, String flowId) {
        scheduleTenantInvoiceGeneration(tenantId, invoiceDate, delay, flowId, null);
    }

    public void scheduleTenantInvoiceGeneration(String tenantId, LocalDate invoiceDate, Duration delay, String flowId, String unitId) {
        String safeFlowId = (flowId == null || flowId.isBlank()) ? "n/a" : flowId;
        String safeUnitId = (unitId == null || unitId.isBlank()) ? null : unitId;
        log.info("Scheduling tenant invoice generation task: flowId={} tenant={} unitId={} billingDate={} delayMs={}",
                safeFlowId, tenantId, safeUnitId, invoiceDate, delay.toMillis());

        CompletableFuture.runAsync(
                () -> generateForTenantUnits(tenantId, invoiceDate, safeFlowId, safeUnitId),
                CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS, invoiceGenerationExecutor)
        ).exceptionally(ex -> {
            log.error("Tenant invoice generation task crashed: flowId={} tenant={} unitId={} billingDate={} reason={}",
                    safeFlowId, tenantId, safeUnitId, invoiceDate, rootMessage(ex));
            return null;
        });
    }

    private void generateForTenantUnits(String tenantId, LocalDate invoiceDate) {
        generateForTenantUnits(tenantId, invoiceDate, "n/a", null);
    }

    private void generateForTenantUnits(String tenantId, LocalDate invoiceDate, String flowId) {
        generateForTenantUnits(tenantId, invoiceDate, flowId, null);
    }

    private void generateForTenantUnits(String tenantId, LocalDate invoiceDate, String flowId, String unitId) {
        long startedAtMs = System.currentTimeMillis();
        List<String> unitIds;
        try {
            if (unitId != null && !unitId.isBlank()) {
                Unit unit = unitRepository.findById(unitId).orElse(null);
                if (unit == null) {
                    log.warn("Tenant invoice generation skipped: flowId={} tenant={} unit={} billingDate={} reason=unit-not-found",
                            flowId, tenantId, unitId, invoiceDate);
                    return;
                }
                if (!tenantId.equals(unit.getTenantId())) {
                    log.warn("Tenant invoice generation skipped: flowId={} tenant={} unit={} billingDate={} reason=unit-tenant-mismatch",
                            flowId, tenantId, unitId, invoiceDate);
                    return;
                }
                if (!unit.isActive()) {
                    log.warn("Tenant invoice generation skipped: flowId={} tenant={} unit={} billingDate={} reason=unit-inactive",
                            flowId, tenantId, unitId, invoiceDate);
                    return;
                }
                unitIds = List.of(unitId);
            } else {
                unitIds = unitRepository.findByTenantIdAndActive(tenantId, true).stream()
                        .map(unit -> unit.getId())
                        .toList();
            }
        } catch (Exception ex) {
            log.error("Tenant invoice generation failed before unit traversal: flowId={} tenant={} unitId={} billingDate={} reason={}",
                    flowId, tenantId, unitId, invoiceDate, rootMessage(ex));
            return;
        }

        if (unitIds.isEmpty()) {
            log.warn("Tenant invoice generation skipped: flowId={} tenant={} unitId={} billingDate={} reason=no-active-units",
                    flowId, tenantId, unitId, invoiceDate);
            return;
        }

        log.info("Starting tenant invoice generation: flowId={} tenant={} unitId={} billingDate={} unitCount={}",
                flowId, tenantId, unitId, invoiceDate, unitIds.size());

        int successCount = 0;
        int failureCount = 0;
        int skippedExistingCount = 0;

        for (String unitIdForLoop : unitIds) {
            try {
                boolean alreadyGenerated = !invoiceRepository
                        .findAnyByTenantIdAndUnitIdAndYearAndMonth(tenantId, unitIdForLoop, invoiceDate.getYear(), invoiceDate.getMonthValue())
                        .isEmpty();
                if (alreadyGenerated) {
                    skippedExistingCount++;
                    log.debug("Tenant invoice generation skipped: flowId={} tenant={} unit={} cycle={}-{} reason=already-exists",
                            flowId, tenantId, unitIdForLoop, invoiceDate.getYear(), invoiceDate.getMonthValue());
                    continue;
                }

                GenerateInvoiceRequest req = new GenerateInvoiceRequest();
                req.setUnitId(unitIdForLoop);
                req.setYear(invoiceDate.getYear());
                req.setMonth(invoiceDate.getMonthValue());
                req.setIssueDate(invoiceDate.atStartOfDay().toInstant(ZoneOffset.UTC));
                invoiceService.generate(tenantId, req);
                successCount++;
                log.info("Tenant invoice generation succeeded: flowId={} tenant={} unit={} cycle={}-{}",
                        flowId, tenantId, unitIdForLoop, invoiceDate.getYear(), invoiceDate.getMonthValue());
            } catch (Exception ex) {
                failureCount++;
                log.warn("Tenant invoice generation failed: flowId={} tenant={} unit={} cycle={}-{} reason={}",
                        flowId, tenantId, unitIdForLoop, invoiceDate.getYear(), invoiceDate.getMonthValue(), rootMessage(ex));
            }
        }

        long durationMs = System.currentTimeMillis() - startedAtMs;
        log.info("Tenant invoice generation completed: flowId={} tenant={} unitId={} billingDate={} unitCount={} successCount={} skippedExistingCount={} failureCount={} durationMs={}",
                flowId, tenantId, unitId, invoiceDate, unitIds.size(), successCount, skippedExistingCount, failureCount, durationMs);
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
