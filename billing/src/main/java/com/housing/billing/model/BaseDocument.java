package com.housing.billing.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.time.Instant;

@Data
public abstract class BaseDocument {
    private String type;        // e.g. "tenant", "unit", "invoice"
    @NotBlank(message = "tenantId is required")
    @Pattern(regexp = "^(tenant::.+|superadmin)$", message = "Invalid tenantId format")
    private String tenantId;    // EVERY document must have this for isolation
    private Instant createdAt;
    private Instant updatedAt;
}
