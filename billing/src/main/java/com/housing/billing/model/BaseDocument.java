package com.housing.billing.model;

import lombok.Data;
import java.time.Instant;

@Data
public abstract class BaseDocument {
    private String type;        // e.g. "tenant", "unit", "invoice"
    private String tenantId;    // EVERY document must have this for isolation
    private Instant createdAt;
    private Instant updatedAt;
}
