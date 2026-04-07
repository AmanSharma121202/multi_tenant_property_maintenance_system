package com.housing.billing.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdatePaymentRequest {
    @Schema(example = "2500")
    @Positive(message = "amount must be greater than 0")
    private BigDecimal amount;

    @Schema(example = "UPI")
    @Pattern(regexp = "^(CASH|BANK_TRANSFER|UPI|CHEQUE)$",
            message = "method must be one of CASH, BANK_TRANSFER, UPI, CHEQUE")
    private String method;

    @Schema(example = "Rent for April")
    @Pattern(regexp = ".*\\S.*", message = "notes cannot be blank")
    private String notes;

    @Schema(example = "UPI-APR-2026-0001")
    @Pattern(regexp = ".*\\S.*", message = "txnRef cannot be blank")
    private String txnRef;
}
