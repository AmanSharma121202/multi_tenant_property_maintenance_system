package com.housing.billing.scheduler;

import com.housing.billing.service.AsyncInvoiceGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceGenerationDispatchScheduler {

    private final AsyncInvoiceGenerationService asyncInvoiceGenerationService;

    @Value("${app.async.invoice-generation.dispatch-batch-size:20}")
    private int dispatchBatchSize;

    @Scheduled(cron = "${app.async.invoice-generation.dispatch-cron:0 */1 * * * *}")
    public void dispatchSubmittedJobs() {
        asyncInvoiceGenerationService.dispatchSubmittedJobs(dispatchBatchSize);
        log.debug("Dispatched up to {} submitted invoice generation jobs", dispatchBatchSize);
    }
}

