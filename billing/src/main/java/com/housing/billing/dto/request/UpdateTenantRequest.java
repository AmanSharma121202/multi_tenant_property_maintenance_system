package com.housing.billing.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import com.housing.billing.validation.AllowedConfigValue;
import com.housing.billing.validation.AllowedValueType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateTenantRequest {
    @Schema(example = "Sunrise Residency")
    @Pattern(regexp = ".*\\S.*", message = "name cannot be blank")
    private String name;

    @Schema(example = "INR")
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
    @AllowedConfigValue(type = AllowedValueType.CURRENCY, message = "currency must be one of configured validCurrency values")
    private String currency;

    @Schema(example = "2026-04-20")
    @JsonProperty("invoice_date")
    private LocalDate invoiceDate;

    @Schema(example = "PERCENTAGE")
    @AllowedConfigValue(type = AllowedValueType.LATE_FEE_TYPE, message = "lateFeeType must be one of configured validLateFeeType values")
    private String lateFeeType;

    @Schema(example = "2.5")
    @PositiveOrZero(message = "lateFeeValue must be zero or positive")
    private Double lateFeeValue;

    @Schema(example = "Tower A, Main Street, Bengaluru")
    @Pattern(regexp = "^$|.*\\S.*", message = "address cannot be blank")
    private String address;
}
