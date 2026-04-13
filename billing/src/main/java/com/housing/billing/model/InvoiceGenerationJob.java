package com.housing.billing.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.repository.Collection;
import org.springframework.data.couchbase.repository.Scope;

import java.time.Instant;

@Document
@Collection("invoice_generation_jobs")
@Scope("main")
@Data
@EqualsAndHashCode(callSuper = true)
public class InvoiceGenerationJob extends BaseDocument {

    @Id
    private String id;

    @NotBlank(message = "unitId is required")
    @Pattern(regexp = "^unit::.+$", message = "Invalid unitId format")
    private String unitId;

    @NotNull(message = "year is required")
    @Min(value = 2000, message = "Year must be between 2000 and 2100")
    @Max(value = 2100, message = "Year must be between 2000 and 2100")
    private Integer year;

    @NotNull(message = "month is required")
    @Min(value = 1, message = "Month must be between 1 and 12")
    @Max(value = 12, message = "Month must be between 1 and 12")
    private Integer month;

    @NotBlank(message = "status is required")
    @Pattern(regexp = "^(SUBMITTED|RUNNING|SUCCEEDED|FAILED)$",
            message = "Status must be one of: SUBMITTED, RUNNING, SUCCEEDED, FAILED")
    private String status;

    @Pattern(regexp = "^$|^INV-.+", message = "Invalid invoiceId format")
    private String invoiceId;

    @Pattern(regexp = "^$|.*\\S.*", message = "Error message cannot be blank")
    private String errorMessage;

    private Instant startedAt;
    private Instant completedAt;
}

