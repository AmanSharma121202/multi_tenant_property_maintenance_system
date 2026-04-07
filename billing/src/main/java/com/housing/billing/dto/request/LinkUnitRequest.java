package com.housing.billing.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LinkUnitRequest {

    @Schema(example = "unit::123")
    @NotBlank(message = "unitId is required")
    @Pattern(regexp = "^unit::.+$", message = "unitId must match unit::...")
    private String unitId;
}

