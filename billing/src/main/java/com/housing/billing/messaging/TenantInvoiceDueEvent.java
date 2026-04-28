package com.housing.billing.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantInvoiceDueEvent {
    private String eventId;
    private String tenantId;
    private LocalDate billingDate;
    private long delaySeconds;
    private Instant occurredAt;
}

