package com.housing.billing.dto.request;
import lombok.Data;

@Data
public class UpdateOwnerRequest {
    private String name;
    private String email;
    private String phone;
    private String status; // ACTIVE or INACTIVE
}
