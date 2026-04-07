package com.housing.billing.dto.request;

import com.housing.billing.validation.AllowedConfigValue;
import com.housing.billing.validation.AllowedValueType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUnitRequest {
    @Schema(example = "A-101")
    @Pattern(regexp = ".*\\S.*", message = "unitNumber cannot be blank")
    private String unitNumber;

    @Schema(example = "2BHK")
    @Pattern(regexp = ".*\\S.*", message = "profileCode cannot be blank")
    @AllowedConfigValue(type = AllowedValueType.PROFILE_CODE, message = "profileCode must be one of configured validProfileCode values")
    private String profileCode;

    @Schema(example = "true")
    private Boolean active;
}
