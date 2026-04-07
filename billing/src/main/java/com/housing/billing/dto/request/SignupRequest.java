package com.housing.billing.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SignupRequest {
    @Schema(example = "System Super Admin")
    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must not exceed 100 characters")
    private String name;

    @Schema(example = "superadmin@billing.com")
    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    @Size(max = 150, message = "email must not exceed 150 characters")
    private String email;

    @Schema(example = "SuperAdmin@123")
    @NotBlank(message = "password is required")
    @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
    private String password;

    @Schema(example = "superadmin")
    @NotBlank(message = "tenantId is required")
    @Pattern(regexp = "^(tenant::.+|superadmin)$", message = "tenantId must match tenant::... or superadmin")
    private String tenantId;

    @Schema(example = "[\"SUPERADMIN\"]")
    @NotEmpty(message = "at least one role is required")
    @Size(max = 5, message = "roles must not exceed 5 entries")
    private List<@Pattern(regexp = "^(SUPERADMIN|TENANT_ADMIN|CSR|OWNER)$",
            message = "role must be one of SUPERADMIN, TENANT_ADMIN, CSR, OWNER") String> roles;
}
