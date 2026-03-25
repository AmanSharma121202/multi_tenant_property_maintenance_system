package com.housing.billing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProfileRequest {
    @NotBlank
    private String code;
    @NotBlank  private String label;
    @NotNull
    @Positive
    private BigDecimal monthlyAmount;
    private boolean active = true;
}
