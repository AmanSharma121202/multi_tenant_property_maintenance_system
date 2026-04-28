package com.housing.billing.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerUnitLinkedEvent {
    private String eventId;
    private String tenantId;
    private String unitId;
    private String ownerId;
    private Instant occurredAt;
}

