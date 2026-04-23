package com.housing.billing.service;

import com.housing.billing.dto.request.GenerateInvoiceRequest;
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
    private final Executor invoiceGenerationExecutor;

    public AsyncInvoiceGenerationService(UnitRepository unitRepository,
                                         InvoiceService invoiceService,
                                         @Qualifier("invoiceGenerationExecutor") Executor invoiceGenerationExecutor) {
        this.unitRepository = unitRepository;
        this.invoiceService = invoiceService;
        this.invoiceGenerationExecutor = invoiceGenerationExecutor;
    }

    public void scheduleTenantInvoiceGeneration(String tenantId, LocalDate invoiceDate, Duration delay) {
        CompletableFuture.runAsync(
                () -> generateForTenantUnits(tenantId, invoiceDate),
                CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS, invoiceGenerationExecutor)
        );
    }

    private void generateForTenantUnits(String tenantId, LocalDate invoiceDate) {
        List<String> unitIds = unitRepository.findByTenantId(tenantId).stream()
                .map(unit -> unit.getId())
                .toList();

        for (String unitId : unitIds) {
            try {
                GenerateInvoiceRequest req = new GenerateInvoiceRequest();
                req.setUnitId(unitId);
                req.setYear(invoiceDate.getYear());
                req.setMonth(invoiceDate.getMonthValue());
                req.setIssueDate(invoiceDate.atStartOfDay().toInstant(ZoneOffset.UTC));
                invoiceService.generate(tenantId, req);
            } catch (Exception ex) {
                log.warn("Tenant invoice generation failed for tenant={} unit={}: {}", tenantId, unitId, rootMessage(ex));
            }
        }
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


