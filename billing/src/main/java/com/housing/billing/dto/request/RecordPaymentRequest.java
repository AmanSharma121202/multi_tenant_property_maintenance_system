package com.housing.billing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecordPaymentRequest {
    @NotBlank
    private String invoiceId;
    @NotBlank  private String unitId;
    @NotBlank  private String ownerId;
    @NotBlank  private String method;      // CASH, BANK_TRANSFER, UPI
    @NotNull
    @Positive
    private BigDecimal amount;
    private String txnRef;
    private String notes;
}
