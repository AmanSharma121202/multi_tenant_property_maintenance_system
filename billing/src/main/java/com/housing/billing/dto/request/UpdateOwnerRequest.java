package com.housing.billing.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateOwnerRequest {
    @Schema(example = "Amit Sharma")
    @Pattern(regexp = ".*\\S.*", message = "name cannot be blank")
    @Size(max = 100, message = "name must not exceed 100 characters")
    private String name;

    @Schema(example = "amit.sharma@example.com")
    @Pattern(regexp = ".*\\S.*", message = "email cannot be blank")
    @Email(message = "email must be valid")
    @Size(max = 150, message = "email must not exceed 150 characters")
    private String email;

    @Schema(example = "+91-9876543210")
    @Pattern(regexp = "^$|^[+0-9][0-9\\- ]{6,19}$", message = "phone must be a valid contact number")
    private String phone;

    @Schema(example = "ACTIVE")
    @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "status must be ACTIVE or INACTIVE")
    private String status;
}
