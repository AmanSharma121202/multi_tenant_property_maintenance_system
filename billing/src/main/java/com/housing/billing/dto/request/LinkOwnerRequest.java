package com.housing.billing.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LinkOwnerRequest {
    @NotBlank(message = "Owner ID is required") private String ownerId;
    private boolean primary = true;
}
