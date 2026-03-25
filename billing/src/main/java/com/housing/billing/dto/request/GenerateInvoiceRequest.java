package com.housing.billing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.Instant;

@Data
public class GenerateInvoiceRequest {

    @NotBlank(message = "Unit ID is required")
    private String unitId;

    @NotNull(message = "Year is required")
    private Integer year;

    @NotNull(message = "Month is required")
    private Integer month;

    private Instant dueDate;
    private Instant issueDate;
}