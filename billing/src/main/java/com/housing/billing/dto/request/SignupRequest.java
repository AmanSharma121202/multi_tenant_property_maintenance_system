package com.housing.billing.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SignupRequest {
    @NotBlank  private String name;
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
    @NotBlank
    private String tenantId;
    private List<String> roles; // TENANT_ADMIN, CSR, OWNER
}
