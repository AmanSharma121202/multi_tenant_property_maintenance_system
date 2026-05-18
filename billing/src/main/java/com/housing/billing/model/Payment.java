package com.housing.billing.model;

import com.housing.billing.model.BaseDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.repository.Collection;
import org.springframework.data.couchbase.repository.Scope;

import java.math.BigDecimal;
import java.time.Instant;

@Document
@Collection("payments")
@Scope("main")
@Data
@EqualsAndHashCode(callSuper = true)
public class Payment extends BaseDocument {

    @Id
    private String     id;

    @NotBlank(message = "Unit ID is required")
    @Pattern(regexp = "^unit::.+$", message = "Invalid unitId format")
    private String     unitId;
    @NotBlank(message = "Owner ID is required")
    @Pattern(regexp = "^owner::.+$", message = "Invalid ownerId format")
    private String     ownerId;
    @NotBlank(message = "Payment method is required")
    @Pattern(regexp = "^(CASH|BANK_TRANSFER|UPI|CHEQUE)$",
            message = "Payment method must be one of: CASH, BANK_TRANSFER, UPI, CHEQUE")
    private String     method;       // CASH, BANK_TRANSFER, UPI, CHEQUE
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than 0")
    private BigDecimal amount;
    @Pattern(regexp = "^$|.*\\S.*", message = "Transaction reference cannot be blank")
    private String     txnRef;       // Bank transaction reference number
    private Instant receivedAt;
    @Pattern(regexp = "^$|.*\\S.*", message = "Notes cannot be blank")
    private String     notes;
    @Pattern(regexp = "^$|.*\\S.*", message = "PaidBy cannot be blank")
    private String     paidBy;
}
