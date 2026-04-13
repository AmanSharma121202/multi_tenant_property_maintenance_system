package com.housing.billing.scheduler;

import com.housing.billing.model.InvoiceGenerationJob;
import com.housing.billing.repository.InvoiceGenerationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceGenerationJobRetentionScheduler {

    private final InvoiceGenerationJobRepository jobRepository;

    @Value("${app.async.invoice-generation.retention-days:30}")
    private int retentionDays;

    // Runs daily to clean up completed jobs beyond retention.
    @Scheduled(cron = "${app.async.invoice-generation.cleanup-cron:0 30 0 * * *}")
    public void purgeExpiredCompletedJobs() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        List<InvoiceGenerationJob> expiredCompletedJobs = jobRepository.findAll().stream()
                .filter(job -> ("SUCCEEDED".equals(job.getStatus()) || "FAILED".equals(job.getStatus())))
                .filter(job -> job.getCompletedAt() != null && job.getCompletedAt().isBefore(cutoff))
                .toList();

        if (expiredCompletedJobs.isEmpty()) {
            return;
        }

        jobRepository.deleteAll(expiredCompletedJobs);
        log.info("Purged {} invoice generation jobs older than {} days", expiredCompletedJobs.size(), retentionDays);
    }
}

