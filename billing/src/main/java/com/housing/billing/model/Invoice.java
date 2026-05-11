package com.housing.billing.model;

import com.housing.billing.model.BaseDocument;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Field;
import org.springframework.data.couchbase.repository.Collection;
import org.springframework.data.couchbase.repository.Scope;

import java.math.BigDecimal;
import java.time.Instant;

@Document
@Collection("invoices")
@Scope("main")
@Data
@EqualsAndHashCode(callSuper = true)
public class Invoice extends BaseDocument {

    @Id
    private String id;  // Pattern: INV-{unitId}-{YYYY}{MM} e.g. INV-A101-202501
    @NotBlank(message = "Unit ID is required")
    @Pattern(regexp = "^unit::.+$", message = "Invalid unitId format")
    private String     unitId;
    @Pattern(regexp = "^$|^owner::.+$", message = "Invalid ownerId format")
    private String     ownerId;
    @Min(value = 2000, message = "Year must be between 2000 and 2100")
    @Max(value = 2100, message = "Year must be between 2000 and 2100")
    private int        year;
    @Min(value = 1, message = "Month must be between 1 and 12")
    @Max(value = 12, message = "Month must be between 1 and 12")
    private int        month;
    @NotNull(message = "Opening balance is required")
    @PositiveOrZero(message = "Opening balance must be zero or positive")
    private BigDecimal openingBalance;    // Previous month's closing balance
    @NotNull(message = "Current charges are required")
    @PositiveOrZero(message = "Current charges must be zero or positive")
    private BigDecimal currentCharges;   // Monthly charge from Profile
    @NotNull(message = "Late fee is required")
    @PositiveOrZero(message = "Late fee must be zero or positive")
    private BigDecimal lateFee;          // Applied by scheduler
    @NotNull(message = "Adjustments are required")
    private BigDecimal adjustments;      // Manual adjustments
    @NotNull(message = "Payments in period are required")
    @PositiveOrZero(message = "Payments in period must be zero or positive")
    private BigDecimal paymentsInPeriod; // Payments received this month
    @NotNull(message = "Closing balance is required")
    private BigDecimal closingBalance;   // = opening + charges + lateFee + adj - payments
    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(PAID|PARTIAL|DUE|OVERDUE)$", message = "Status must be one of: PAID, PARTIAL, DUE, OVERDUE")
    private String     status;           // PAID / PARTIAL / DUE / OVERDUE
    @NotNull(message = "Issue date is required")
    private Instant issueDate;
    @NotNull(message = "Due date is required")
    private Instant    dueDate;
    @Field("payment_date")
    private Instant    paymentDate;
}
