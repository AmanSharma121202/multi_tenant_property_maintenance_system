package com.housing.billing.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateTenantRequest {
    @NotBlank private String name;
    @NotBlank private String currency;    // e.g. INR
    @NotNull  private Integer billingDay; // 1-28
    private String lateFeeType;           // PERCENTAGE or FIXED
    private double lateFeeValue;          // e.g. 2.0
    private String address;
}
