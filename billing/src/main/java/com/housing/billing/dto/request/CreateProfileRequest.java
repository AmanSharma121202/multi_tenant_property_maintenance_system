package com.housing.billing.dto.request;

import com.housing.billing.validation.AllowedConfigValue;
import com.housing.billing.validation.AllowedValueType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProfileRequest {
    @Schema(example = "2BHK")
    @NotBlank(message = "code is required")
    @Pattern(regexp = ".*\\S.*", message = "code cannot be blank")
    @AllowedConfigValue(type = AllowedValueType.CODE, message = "code must be one of configured validCode values")
    private String code;

    @Schema(example = "2BHK Deluxe")
    @NotBlank(message = "label is required")
    @Pattern(regexp = ".*\\S.*", message = "label cannot be blank")
    private String label;

    @Schema(example = "12500")
    @NotNull(message = "monthlyAmount is required")
    @Positive(message = "monthlyAmount must be greater than 0")
    private BigDecimal monthlyAmount;

    @Schema(example = "true")
    private boolean active = true;
}


