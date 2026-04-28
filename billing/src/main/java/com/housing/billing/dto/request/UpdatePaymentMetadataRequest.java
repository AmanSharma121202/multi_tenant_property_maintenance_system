package com.housing.billing.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.Instant;

@Data
public class UpdatePaymentMetadataRequest {

    @Schema(example = "UPI-APR-2026-0001")
    @Pattern(regexp = "^$|.*\\S.*", message = "txnRef cannot be blank")
    private String txnRef;

    @Schema(example = "2026-04-24T10:15:30Z")
    private Instant receivedAt;

    @Schema(example = "Rent payment for April")
    @Pattern(regexp = "^$|.*\\S.*", message = "notes cannot be blank")
    private String notes;

    @Schema(example = "Amit Sharma")
    @Pattern(regexp = "^$|.*\\S.*", message = "paidBy cannot be blank")
    private String paidBy;

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("Field '" + fieldName + "' is not allowed in payment metadata update");
    }
}


