package com.housing.billing.scheduler;

import com.housing.billing.model.InvoiceGenerationJob;
import com.housing.billing.repository.InvoiceGenerationJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceGenerationJobRetentionSchedulerTest {

    @Mock
    private InvoiceGenerationJobRepository jobRepository;

    @InjectMocks
    private InvoiceGenerationJobRetentionScheduler scheduler;

    @Test
    void purgeExpiredCompletedJobs_deletesOnlyCompletedJobsOlderThanRetention() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 30);

        InvoiceGenerationJob oldSucceeded = job("job-1", "SUCCEEDED", Instant.now().minus(31, ChronoUnit.DAYS));
        InvoiceGenerationJob oldFailed = job("job-2", "FAILED", Instant.now().minus(40, ChronoUnit.DAYS));
        InvoiceGenerationJob recentSucceeded = job("job-3", "SUCCEEDED", Instant.now().minus(5, ChronoUnit.DAYS));
        InvoiceGenerationJob running = job("job-4", "RUNNING", Instant.now().minus(60, ChronoUnit.DAYS));

        when(jobRepository.findAll()).thenReturn(List.of(oldSucceeded, oldFailed, recentSucceeded, running));

        scheduler.purgeExpiredCompletedJobs();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<InvoiceGenerationJob>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(jobRepository).deleteAll(captor.capture());
        List<InvoiceGenerationJob> deleted = ((List<InvoiceGenerationJob>) captor.getValue());

        assertEquals(2, deleted.size());
        assertEquals(List.of("job-1", "job-2"), deleted.stream().map(InvoiceGenerationJob::getId).toList());
    }

    @Test
    void purgeExpiredCompletedJobs_doesNothingWhenNothingExpired() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 30);

        InvoiceGenerationJob recentSucceeded = job("job-1", "SUCCEEDED", Instant.now().minus(10, ChronoUnit.DAYS));
        InvoiceGenerationJob submitted = job("job-2", "SUBMITTED", null);
        when(jobRepository.findAll()).thenReturn(List.of(recentSucceeded, submitted));

        scheduler.purgeExpiredCompletedJobs();

        verify(jobRepository, never()).deleteAll();
    }

    private InvoiceGenerationJob job(String id, String status, Instant completedAt) {
        InvoiceGenerationJob job = new InvoiceGenerationJob();
        job.setId(id);
        job.setStatus(status);
        job.setCompletedAt(completedAt);
        return job;
    }
}


