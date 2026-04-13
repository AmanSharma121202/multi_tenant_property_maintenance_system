package com.housing.billing.service;

import com.housing.billing.dto.request.GenerateInvoiceRequest;
import com.housing.billing.dto.response.InvoiceGenerationJobResponse;
import com.housing.billing.model.Invoice;
import com.housing.billing.model.InvoiceGenerationJob;
import com.housing.billing.repository.InvoiceGenerationJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncInvoiceGenerationServiceTest {

    @Mock
    private InvoiceGenerationJobRepository jobRepository;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private ModelValidationService modelValidationService;

    @Test
    void submit_existingNaturalKeyJob_returnsExistingWithoutReprocessing() {
        GenerateInvoiceRequest req = baseRequest();
        String jobId = "inv-job::tenant::1::unit::101::202604";

        InvoiceGenerationJob existing = new InvoiceGenerationJob();
        existing.setId(jobId);
        existing.setTenantId("tenant::1");
        existing.setStatus("SUCCEEDED");
        existing.setInvoiceId("INV-unit::101-202604");
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(existing));

        AsyncInvoiceGenerationService service = new AsyncInvoiceGenerationService(
                jobRepository,
                invoiceService,
                modelValidationService,
                Runnable::run
        );

        InvoiceGenerationJobResponse response = service.submit("tenant::1", req);

        assertEquals(jobId, response.getJobId());
        assertEquals("SUCCEEDED", response.getStatus());
        assertEquals("INV-unit::101-202604", response.getInvoiceId());
        verify(invoiceService, never()).generate(any(), any());
    }

    @Test
    void submit_newJob_persistsAndTransitionsToSucceeded() {
        GenerateInvoiceRequest req = baseRequest();
        String jobId = "inv-job::tenant::1::unit::101::202604";

        Map<String, InvoiceGenerationJob> jobStore = new HashMap<>();
        when(jobRepository.findById(any())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0, String.class);
            return Optional.ofNullable(jobStore.get(id));
        });
        when(jobRepository.save(any(InvoiceGenerationJob.class))).thenAnswer(invocation -> {
            InvoiceGenerationJob job = invocation.getArgument(0, InvoiceGenerationJob.class);
            jobStore.put(job.getId(), job);
            return job;
        });

        Invoice invoice = new Invoice();
        invoice.setId("INV-unit::101-202604");
        when(invoiceService.generate(eq("tenant::1"), any(GenerateInvoiceRequest.class))).thenReturn(invoice);

        AsyncInvoiceGenerationService service = new AsyncInvoiceGenerationService(
                jobRepository,
                invoiceService,
                modelValidationService,
                Runnable::run
        );

        InvoiceGenerationJobResponse response = service.submit("tenant::1", req);

        assertEquals(jobId, response.getJobId());
        assertTrue("SUBMITTED".equals(response.getStatus()) || "SUCCEEDED".equals(response.getStatus()));

        InvoiceGenerationJob persisted = jobStore.get(jobId);
        assertNotNull(persisted);
        assertEquals("SUCCEEDED", persisted.getStatus());
        assertEquals("INV-unit::101-202604", persisted.getInvoiceId());

        verify(invoiceService).generate(eq("tenant::1"), any(GenerateInvoiceRequest.class));
        verify(modelValidationService).validate(any(InvoiceGenerationJob.class));
    }

    private GenerateInvoiceRequest baseRequest() {
        GenerateInvoiceRequest req = new GenerateInvoiceRequest();
        req.setUnitId("unit::101");
        req.setYear(2026);
        req.setMonth(4);
        return req;
    }
}


