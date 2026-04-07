package com.housing.billing.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateOwnerRequest {
    @Schema(example = "Amit Sharma")
    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must not exceed 100 characters")
    @Pattern(regexp = ".*\\S.*", message = "name cannot be blank")
    private String name;
    
    @Schema(example = "amit.sharma@example.com")
    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    @Size(max = 150, message = "email must not exceed 150 characters")
    private String email;
    
    @Schema(example = "+91-9876543210")
    @NotBlank(message = "phone is required")
    @Pattern(regexp = "^[+0-9][0-9\\- ]{6,19}$", message = "phone must be a valid contact number")
    private String phone;
    
    @Schema(example = "ACTIVE")
    @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "status must be ACTIVE or INACTIVE")
    private String status = "ACTIVE";
}
