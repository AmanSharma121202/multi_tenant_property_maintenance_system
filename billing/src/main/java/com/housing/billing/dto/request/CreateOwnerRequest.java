package com.housing.billing.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateOwnerRequest {
    @NotBlank private String name;
    @NotBlank @Email private String email;
    private String phone;
    private String status = "ACTIVE";
}
