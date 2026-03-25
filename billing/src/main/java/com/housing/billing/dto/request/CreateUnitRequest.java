package com.housing.billing.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUnitRequest {
    @NotBlank(message = "Unit number is required")
    private String unitNumber;

    @NotBlank(message = "Profile code is required")
    private String profileCode;

    private boolean active = true;
}
