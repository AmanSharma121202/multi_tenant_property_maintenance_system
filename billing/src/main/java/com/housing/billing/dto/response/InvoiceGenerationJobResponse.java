package com.housing.billing.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class InvoiceGenerationJobResponse {
    String jobId;
    String status;
    String invoiceId;
    String errorMessage;
    Instant createdAt;
    Instant updatedAt;
    Instant startedAt;
    Instant completedAt;
}

