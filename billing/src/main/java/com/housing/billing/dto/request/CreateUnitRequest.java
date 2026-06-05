package com.housing.billing.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.housing.billing.validation.AllowedConfigValue;
import com.housing.billing.validation.AllowedValueType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateUnitRequest {
    @Schema(example = "A-101")
    @NotBlank(message = "unitNumber is required")
    @Pattern(regexp = ".*\\S.*", message = "unitNumber cannot be blank")
    private String unitNumber;

    @Schema(example = "2BHK")
    @NotBlank(message = "profileCode is required")
    @Pattern(regexp = ".*\\S.*", message = "profileCode cannot be blank")
    @AllowedConfigValue(type = AllowedValueType.PROFILE_CODE, message = "profileCode must be one of configured validProfileCode values")
    private String profileCode;

    @Schema(example = "true")
    private boolean active = true;

    @Schema(example = "2026-01-15")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate unitStartDate;
}
