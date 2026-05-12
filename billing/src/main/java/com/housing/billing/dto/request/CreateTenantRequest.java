package com.housing.billing.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import com.housing.billing.validation.AllowedConfigValue;
import com.housing.billing.validation.AllowedValueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateTenantRequest {
    @Schema(example = "Sunrise Residency")
    @NotBlank(message = "name is required")
    @Pattern(regexp = ".*\\S.*", message = "name cannot be blank")
    private String name;
    
    @Schema(example = "INR")
    @NotBlank(message = "currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
    @AllowedConfigValue(type = AllowedValueType.CURRENCY, message = "currency must be one of configured validCurrency values")
    private String currency;    // e.g. INR
    
    @Schema(example = "2026-04-20")
    @NotNull(message = "billing_date is required")
    @JsonProperty("billing_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate billingDate;

    @Schema(example = "PERCENTAGE")
    @NotBlank(message = "lateFeeType is required")
    @AllowedConfigValue(type = AllowedValueType.LATE_FEE_TYPE, message = "lateFeeType must be one of configured validLateFeeType values")
    private String lateFeeType;
    
    @Schema(example = "2.5")
    @NotNull(message = "lateFeeValue is required")
    @PositiveOrZero(message = "lateFeeValue must be zero or positive")
    private Double lateFeeValue;
    
    @Schema(example = "Tower A, Main Street, Bengaluru")
    @Pattern(regexp = "^$|.*\\S.*", message = "address cannot be blank")
    private String address;
}
