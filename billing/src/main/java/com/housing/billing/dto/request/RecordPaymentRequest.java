package com.housing.billing.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecordPaymentRequest {
    @Schema(example = "INV-unit::123-202604")
    @NotBlank(message = "invoiceId is required")
    @Pattern(regexp = "^INV-.+", message = "invoiceId must start with INV-")
    private String invoiceId;
    
    @Schema(example = "UPI")
    @NotBlank(message = "method is required")
    @Pattern(regexp = "^(CASH|BANK_TRANSFER|UPI|CHEQUE)$",
            message = "method must be one of CASH, BANK_TRANSFER, UPI, CHEQUE")
    private String method;
    
    @Schema(example = "2500")
    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than 0")
    private BigDecimal amount;
    
    @Schema(example = "UPI-APR-2026-0001")
    @Pattern(regexp = "^$|.*\\S.*", message = "txnRef cannot be blank")
    private String txnRef;
    
    @Schema(example = "Rent payment for April")
    @Pattern(regexp = "^$|.*\\S.*", message = "notes cannot be blank")
    private String notes;

    @Schema(example = "Amit Sharma")
    @Pattern(regexp = "^$|.*\\S.*", message = "paidBy cannot be blank")
    private String paidBy;
}
