package com.housing.billing.dto.request;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LinkOwnerRequest {
    @Schema(example = "owner::456")
    @NotBlank(message = "ownerId is required")
    @Pattern(regexp = "^owner::.+$", message = "ownerId must match owner::...")
    private String ownerId;

    @Schema(example = "true")
    private boolean primary = true;
}
