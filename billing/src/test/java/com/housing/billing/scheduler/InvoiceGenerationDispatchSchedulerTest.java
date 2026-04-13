package com.housing.billing.scheduler;

import com.housing.billing.service.AsyncInvoiceGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InvoiceGenerationDispatchSchedulerTest {

    @Mock
    private AsyncInvoiceGenerationService asyncInvoiceGenerationService;

    @InjectMocks
    private InvoiceGenerationDispatchScheduler scheduler;

    @Test
    void dispatchSubmittedJobs_usesConfiguredBatchSize() {
        ReflectionTestUtils.setField(scheduler, "dispatchBatchSize", 15);

        scheduler.dispatchSubmittedJobs();

        verify(asyncInvoiceGenerationService).dispatchSubmittedJobs(15);
    }
}

