package com.housing.billing.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.housing.billing.validation.AllowedConfigValue;
import com.housing.billing.validation.AllowedValueType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class UpdateTenantRequest {
    @Schema(example = "Sunrise Residency")
    @Pattern(regexp = ".*\\S.*", message = "name cannot be blank")
    private String name;

    @Schema(example = "INR")
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
    @AllowedConfigValue(type = AllowedValueType.CURRENCY, message = "currency must be one of configured validCurrency values")
    private String currency;

    @Schema(example = "5")
    @Min(value = 1, message = "billingDay must be between 1 and 28")
    @Max(value = 28, message = "billingDay must be between 1 and 28")
    private Integer billingDay;

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
