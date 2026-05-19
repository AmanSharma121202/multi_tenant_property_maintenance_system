package com.housing.billing.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class GenerateTenantInvoicesRequest {

    @Schema(example = "2026")
    @NotNull(message = "year is required")
    @Min(value = 2000, message = "year must be between 2000 and 2100")
    @Max(value = 2100, message = "year must be between 2000 and 2100")
    private Integer year;

    @Schema(example = "4")
    @NotNull(message = "month is required")
    @Min(value = 1, message = "month must be between 1 and 12")
    @Max(value = 12, message = "month must be between 1 and 12")
    private Integer month;

    @Schema(example = "unit::123")
    @Pattern(regexp = "^unit::.+$", message = "unitId must match unit::...")
    private String unitId;
}
