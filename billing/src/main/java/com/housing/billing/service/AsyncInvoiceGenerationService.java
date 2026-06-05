package com.housing.billing.service;

import com.housing.billing.dto.request.GenerateInvoiceRequest;
import com.housing.billing.dto.response.TenantInvoiceGenerationResult;
import com.housing.billing.model.Unit;
import com.housing.billing.repository.InvoiceRepository;
import com.housing.billing.repository.UnitRepository;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
@Slf4j
public class AsyncInvoiceGenerationService {

    private final UnitRepository unitRepository;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;

    public AsyncInvoiceGenerationService(UnitRepository unitRepository,
                                         InvoiceService invoiceService,
                                         InvoiceRepository invoiceRepository) {
        this.unitRepository = unitRepository;
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
    }

    public TenantInvoiceGenerationResult scheduleTenantInvoiceGeneration(
            String tenantId, LocalDate invoiceDate, Duration delay) {
        return scheduleTenantInvoiceGeneration(tenantId, invoiceDate, delay, null, null);
    }

    public TenantInvoiceGenerationResult scheduleTenantInvoiceGeneration(
            String tenantId, LocalDate invoiceDate, Duration delay, String flowId) {
        return scheduleTenantInvoiceGeneration(tenantId, invoiceDate, delay, flowId, null);
    }

    public TenantInvoiceGenerationResult scheduleTenantInvoiceGeneration(
            String tenantId, LocalDate invoiceDate, Duration delay, String flowId, String unitId) {
        String safeFlowId = (flowId == null || flowId.isBlank()) ? "n/a" : flowId;
        String safeUnitId = (unitId == null || unitId.isBlank()) ? null : unitId;
        long delayMs = (delay == null) ? 0L : Math.max(0L, delay.toMillis());
        if (delayMs > 0L) {
            log.info("Kafka delay already handled; executing immediately: flowId={} tenant={} unitId={} billingDate={} originalDelayMs={}",
                    safeFlowId, tenantId, safeUnitId, invoiceDate, delayMs);
        } else {
            log.info("Dispatching tenant invoice generation immediately: flowId={} tenant={} unitId={} billingDate={}",
                    safeFlowId, tenantId, safeUnitId, invoiceDate);
        }

        try {
            return generateForTenantUnits(tenantId, invoiceDate, safeFlowId, safeUnitId);
        } catch (Exception ex) {
            log.error("Tenant invoice generation task crashed: flowId={} tenant={} unitId={} billingDate={} reason={}",
                    safeFlowId, tenantId, safeUnitId, invoiceDate, rootMessage(ex));
            return TenantInvoiceGenerationResult.empty();
        }
    }

    private TenantInvoiceGenerationResult generateForTenantUnits(
            String tenantId, LocalDate invoiceDate, String flowId, String unitId) {
        long startedAtMs = System.currentTimeMillis();
        List<String> unitIds;
        try {
            if (unitId != null && !unitId.isBlank()) {
                Unit unit = unitRepository.findById(unitId).orElse(null);
                if (unit == null) {
                    log.warn("Tenant invoice generation skipped: flowId={} tenant={} unit={} billingDate={} reason=unit-not-found",
                            flowId, tenantId, unitId, invoiceDate);
                    return TenantInvoiceGenerationResult.empty();
                }
                if (!tenantId.equals(unit.getTenantId())) {
                    log.warn("Tenant invoice generation skipped: flowId={} tenant={} unit={} billingDate={} reason=unit-tenant-mismatch",
                            flowId, tenantId, unitId, invoiceDate);
                    return TenantInvoiceGenerationResult.empty();
                }
                if (!unit.isActive()) {
                    log.warn("Tenant invoice generation skipped: flowId={} tenant={} unit={} billingDate={} reason=unit-inactive",
                            flowId, tenantId, unitId, invoiceDate);
                    return TenantInvoiceGenerationResult.empty();
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
            return TenantInvoiceGenerationResult.empty();
        }

        if (unitIds.isEmpty()) {
            log.warn("Tenant invoice generation skipped: flowId={} tenant={} unitId={} billingDate={} reason=no-active-units",
                    flowId, tenantId, unitId, invoiceDate);
            return TenantInvoiceGenerationResult.empty();
        }

        log.info("Starting tenant invoice generation: flowId={} tenant={} unitId={} billingDate={} unitCount={}",
                flowId, tenantId, unitId, invoiceDate, unitIds.size());

        int successCount = 0;
        int failureCount = 0;
        int skippedExistingCount = 0;
        int skippedBeforeStartDateCount = 0;

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

                // Check unitStartDate: skip if the entire cycle starts before the unit existed
                Unit unitForLoop = unitRepository.findById(unitIdForLoop).orElse(null);
                if (unitForLoop != null && unitForLoop.getUnitStartDate() != null) {
                    LocalDate cycleStart = LocalDate.of(invoiceDate.getYear(), invoiceDate.getMonthValue(), 1);
                    if (cycleStart.isBefore(unitForLoop.getUnitStartDate())) {
                        skippedBeforeStartDateCount++;
                        log.debug("Tenant invoice generation skipped: flowId={} tenant={} unit={} cycle={}-{} reason=before-unit-start-date unitStartDate={}",
                                flowId, tenantId, unitIdForLoop, invoiceDate.getYear(), invoiceDate.getMonthValue(), unitForLoop.getUnitStartDate());
                        continue;
                    }
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
        log.info("Tenant invoice generation completed: flowId={} tenant={} unitId={} billingDate={} unitCount={} successCount={} skippedExistingCount={} skippedBeforeStartDateCount={} failureCount={} durationMs={}",
                flowId, tenantId, unitId, invoiceDate, unitIds.size(), successCount, skippedExistingCount, skippedBeforeStartDateCount, failureCount, durationMs);
        return new TenantInvoiceGenerationResult(unitIds.size(), successCount, skippedExistingCount, failureCount);
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
