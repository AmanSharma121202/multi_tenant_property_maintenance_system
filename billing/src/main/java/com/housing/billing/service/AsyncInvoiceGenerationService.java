package com.housing.billing.service;

import com.housing.billing.dto.request.GenerateInvoiceRequest;
import com.housing.billing.dto.response.InvoiceGenerationJobResponse;
import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.exception.TenantIsolationException;
import com.housing.billing.model.Invoice;
import com.housing.billing.model.InvoiceGenerationJob;
import com.housing.billing.repository.InvoiceGenerationJobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;

@Service
public class AsyncInvoiceGenerationService {

    private final InvoiceGenerationJobRepository jobRepository;
    private final InvoiceService invoiceService;
    private final ModelValidationService modelValidationService;

    private final Executor invoiceGenerationExecutor;

    public AsyncInvoiceGenerationService(InvoiceGenerationJobRepository jobRepository,
                                         InvoiceService invoiceService,
                                         ModelValidationService modelValidationService,
                                         @Qualifier("invoiceGenerationExecutor") Executor invoiceGenerationExecutor) {
        this.jobRepository = jobRepository;
        this.invoiceService = invoiceService;
        this.modelValidationService = modelValidationService;
        this.invoiceGenerationExecutor = invoiceGenerationExecutor;
    }

    public InvoiceGenerationJobResponse enqueue(String tenantId, GenerateInvoiceRequest req) {
        String jobId = buildNaturalJobId(tenantId, req);
        Optional<InvoiceGenerationJob> existing = jobRepository.findById(jobId);
        if (existing.isPresent()) {
            ensureTenant(tenantId, existing.get());
            return toResponse(existing.get());
        }

        InvoiceGenerationJob job = new InvoiceGenerationJob();
        job.setId(jobId);
        job.setType("invoice_generation_job");
        job.setTenantId(tenantId);
        job.setUnitId(req.getUnitId());
        job.setYear(req.getYear());
        job.setMonth(req.getMonth());
        job.setIssueDate(req.getIssueDate());
        job.setDueDate(req.getDueDate());
        job.setStatus("SUBMITTED");
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        modelValidationService.validate(job);

        InvoiceGenerationJob saved = jobRepository.save(job);
        return toResponse(saved);
    }

    public void dispatchSubmittedJobs(int batchSize) {
        List<InvoiceGenerationJob> submittedJobs = jobRepository.findAll().stream()
                .filter(job -> "SUBMITTED".equals(job.getStatus()))
                .sorted(Comparator.comparing(InvoiceGenerationJob::getCreatedAt))
                .limit(Math.max(batchSize, 1))
                .toList();

        submittedJobs.forEach(job -> startGenerationInBackground(job.getId(), job.getTenantId(), requestFromJob(job)));
    }

    public InvoiceGenerationJobResponse getStatus(String tenantId, String jobId) {
        InvoiceGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice generation job not found"));
        ensureTenant(tenantId, job);
        return toResponse(job);
    }


    private void startGenerationInBackground(String jobId, String tenantId, GenerateInvoiceRequest req) {
        CompletableFuture
                .supplyAsync(() -> runGenerationTask(jobId, tenantId, req), invoiceGenerationExecutor)
                .thenAccept(invoice -> markSucceeded(jobId, invoice.getId()))
                .exceptionally(ex -> {
                    markFailed(jobId, rootMessage(ex));
                    return null;
                });
    }

    private Invoice runGenerationTask(String jobId, String tenantId, GenerateInvoiceRequest req) {
        InvoiceGenerationJob running = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice generation job not found"));
        ensureTenant(tenantId, running);

        if (!"SUBMITTED".equals(running.getStatus())) {
            return null;
        }

        running.setStatus("RUNNING");
        running.setStartedAt(Instant.now());
        running.setUpdatedAt(Instant.now());
        jobRepository.save(running);

        FutureTask<Invoice> futureTask = new FutureTask<>(() -> invoiceService.generate(tenantId, req));
        futureTask.run();

        try {
            return futureTask.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        }
    }

    private void markSucceeded(String jobId, String invoiceId) {
        if (invoiceId == null) {
            return;
        }
        InvoiceGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice generation job not found"));
        job.setStatus("SUCCEEDED");
        job.setInvoiceId(invoiceId);
        job.setErrorMessage(null);
        job.setCompletedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
    }

    private void markFailed(String jobId, String errorMessage) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus("FAILED");
            job.setErrorMessage(errorMessage);
            job.setCompletedAt(Instant.now());
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);
        });
    }

    private String buildNaturalJobId(String tenantId, GenerateInvoiceRequest req) {
        return String.format("inv-job::%s::%s::%d%02d", tenantId, req.getUnitId(), req.getYear(), req.getMonth());
    }

    private GenerateInvoiceRequest requestFromJob(InvoiceGenerationJob job) {
        GenerateInvoiceRequest request = new GenerateInvoiceRequest();
        request.setUnitId(job.getUnitId());
        request.setYear(job.getYear());
        request.setMonth(job.getMonth());
        request.setIssueDate(job.getIssueDate());
        request.setDueDate(job.getDueDate());
        return request;
    }

    private void ensureTenant(String tenantId, InvoiceGenerationJob job) {
        if (!tenantId.equals(job.getTenantId())) {
            throw new TenantIsolationException("Tenant isolation violation");
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

    private InvoiceGenerationJobResponse toResponse(InvoiceGenerationJob job) {
        return InvoiceGenerationJobResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus())
                .invoiceId(job.getInvoiceId())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }
}


