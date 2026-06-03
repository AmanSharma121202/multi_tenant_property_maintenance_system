package com.housing.billing.dto.response;

public record TenantInvoiceGenerationResult(
        int units,
        int created,
        int skipped,
        int failed
) {
    public static TenantInvoiceGenerationResult empty() {
        return new TenantInvoiceGenerationResult(0, 0, 0, 0);
    }
}
